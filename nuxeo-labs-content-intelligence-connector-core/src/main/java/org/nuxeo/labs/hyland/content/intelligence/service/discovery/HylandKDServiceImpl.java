package org.nuxeo.labs.hyland.content.intelligence.service.discovery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.labs.hyland.content.intelligence.AuthenticationToken;
import org.nuxeo.labs.hyland.content.intelligence.AuthenticationTokenDiscovery;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCall;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.labs.hyland.content.intelligence.service.ServicesUtils;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.HylandKEServiceImpl;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.model.Extension;

/**
 * This class handles calls to Content Intelligence Knowledge Discovery service
 * 
 * @since 2023
 */
public class HylandKDServiceImpl extends DefaultComponent implements HylandKDService {

    private static final Logger log = LogManager.getLogger(HylandKDServiceImpl.class);

    // Will add "/connect/token" to this endpoint.
    public static final String AUTH_BASE_URL_PARAM = HylandKEServiceImpl.AUTH_BASE_URL_PARAM;

    public static final String AUTH_ENDPOINT = HylandKEServiceImpl.AUTH_ENDPOINT;

    public static final String DISCOVERY_CLIENT_ID_PARAM = "nuxeo.hyland.cic.discovery.clientId";

    public static final String DISCOVERY_CLIENT_SECRET_PARAM = "nuxeo.hyland.cic.discovery.clientSecret";

    public static final String DISCOVERY_BASE_URL_PARAM = "nuxeo.hyland.cic.discovery.baseUrl";

    public static final String DISCOVERY_ENVIRONMENT_PARAM = "nuxeo.hyland.cic.discovery.environment";

    public static final String DISCOVERY_DEFAULT_SOURCEID_PARAM = "nuxeo.hyland.cic.discovery.default.sourceId";

    public static final String DISCOVERY_DEFAULT_AGENTID_PARAM = "nuxeo.hyland.cic.discovery.default.agentId";

    public static final String PULL_RESULTS_MAX_TRIES_PARAM = "nuxeo.hyland.cic.discovery.pullResultsMaxTries";

    public static final int PULL_RESULTS_MAX_TRIES_DEFAULT = 10;

    public static final String PULL_RESULTS_SLEEP_INTERVAL_PARAM = "nuxeo.hyland.cic.discovery.pullResultsSleepInterval";

    public static final int PULL_RESULTS_SLEEP_INTERVAL_DEFAULT = 3000;

    protected static Map<String, AuthenticationToken> discoveryAuthTokens = null;

    protected static String defaultSourceId;

    protected static String defaultAgentId;

    protected static int pullResultsMaxTries;

    protected static int pullResultsSleepIntervalMS;

    protected static ServiceCall serviceCall = new ServiceCall();

    // ====================> Extensions points
    protected static final String EXT_POINT_KD = "knowledgeDiscovery";

    protected Map<String, KDDescriptor> kdContribs = new HashMap<String, KDDescriptor>();

    public static final String CONFIG_DEFAULT = "default";

    public HylandKDServiceImpl() {
        initialize();
    }

    protected void initialize() {

        defaultSourceId = Framework.getProperty(DISCOVERY_DEFAULT_SOURCEID_PARAM);
        defaultAgentId = Framework.getProperty(DISCOVERY_DEFAULT_AGENTID_PARAM);
        pullResultsMaxTries = ServicesUtils.configParamToInt(PULL_RESULTS_MAX_TRIES_PARAM,
                PULL_RESULTS_MAX_TRIES_DEFAULT);
        pullResultsSleepIntervalMS = ServicesUtils.configParamToInt(PULL_RESULTS_SLEEP_INTERVAL_PARAM,
                PULL_RESULTS_SLEEP_INTERVAL_DEFAULT);

    }

    protected KDDescriptor getDescriptor(String configName) {

        if (StringUtils.isBlank(configName)) {
            configName = CONFIG_DEFAULT;
        }

        return kdContribs.get(configName);
    }

    protected String getToken(String configName) {

        if (StringUtils.isBlank(configName)) {
            configName = CONFIG_DEFAULT;
        }

        AuthenticationToken token = discoveryAuthTokens.get(configName);

        return token.getToken();
    }

    @Override
    public ServiceCallResult invokeDiscovery(String configName, String httpMethod, String endpoint,
            String jsonPayload) {
        return invokeDiscovery(configName, httpMethod, endpoint, jsonPayload, null);
    }

    @Override
    public ServiceCallResult invokeDiscovery(String configName, String httpMethod, String endpoint, String jsonPayload,
            Map<String, String> extraHeaders) {

        ServiceCallResult result = null;

        // Get auth token
        String bearer = getToken(configName);
        if (StringUtils.isBlank(bearer)) {
            throw new NuxeoException("No authentication info for calling the Enrichment service.");
        }

        // URL/endpoint
        KDDescriptor config = getDescriptor(configName);
        String targetUrl = config.getBaseUrl();
        if (!endpoint.startsWith("/")) {
            targetUrl += "/";
        }
        targetUrl += endpoint;

        // Headers
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Accept", "*/*");
        headers.put("Authorization", "Bearer " + bearer);
        headers.put("Content-Type", "application/json");
        // More specific Discovery
        headers.put("Hxp-Environment", config.getEnvironment());
        headers.put("Hxp-App", "hxai-discovery");

        // Extra headers
        if (extraHeaders != null && extraHeaders.size() > 0) {
            for (String headerName : extraHeaders.keySet()) {
                headers.put(headerName, extraHeaders.get(headerName));
            }
        }

        // Run
        httpMethod = httpMethod.toUpperCase();
        switch (httpMethod) {
        case "GET":
            result = serviceCall.get(targetUrl, headers);
            break;

        case "POST":
            result = serviceCall.post(targetUrl, headers, jsonPayload);
            break;

        case "PUT":
            result = serviceCall.put(targetUrl, headers, jsonPayload);
            break;

        default:
            throw new NuxeoException("Only GET, POST and PUT are supported.");
        }

        return result;
    }

    // ======================================================================
    // ======================================================================
    // Utility/Specialized methods
    // ======================================================================
    // ======================================================================
    @Override
    public ServiceCallResult getAllAgents(String configName, Map<String, String> extraHeaders) {

        ServiceCallResult result = invokeDiscovery(configName, "GET", "/agent/agents", null, extraHeaders);

        return result;
    }

    @Override
    public ServiceCallResult askQuestion(String configName, String agentId, String question,
            List<String> contextObjectIds, String extraPayloadJsonStr, Map<String, String> extraHeaders) {

        ServiceCallResult result = null;

        // 1. Get an agent (we use any agent here)
        if (StringUtils.isBlank(agentId)) {
            agentId = defaultAgentId;
        }
        if (StringUtils.isBlank(agentId)) {
            throw new NuxeoException("No agentId");
        }

        // 2. Call service with this agent
        String endPoint = "/agent/agents/" + agentId + "/questions";
        JSONObject payload = new JSONObject();
        payload.put("question", question);
        JSONArray contextObjectIdsArray = new JSONArray(contextObjectIds);
        payload.put("contextObjectIds", contextObjectIdsArray);

        if (StringUtils.isNotBlank(extraPayloadJsonStr)) {
            JSONObject extraJson = new JSONObject(extraPayloadJsonStr);
            Iterator<String> keys = extraJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                payload.put(key, extraJson.get(key));
            }
        }

        result = invokeDiscovery(configName, "POST", endPoint, payload.toString(), extraHeaders);

        return result;
    }

    @Override
    public ServiceCallResult getAnswer(String configName, String questionId, Map<String, String> extraHeaders)
            throws InterruptedException {

        ServiceCallResult result = null;

        // Get the answer. This is a loop-pull. For now, we hard code the tries
        String endPoint = "/qna/questions/" + questionId + "/answer";
        int count = 0;
        JSONObject response;
        boolean gotIt = false;
        do {
            count += 1;
            if (count > 2) {
                log.info("getAnswer(), trying to get an answer to question ID " + questionId + ", call " + count + "/"
                        + pullResultsMaxTries + ".");
            } else if (count > 9) {
                log.warn("getAnswer(), trying to get an answer to question ID " + questionId + ", call " + count + "/"
                        + pullResultsMaxTries + ".");
            }

            result = invokeDiscovery(configName, "GET", endPoint, null, extraHeaders);
            if (!result.callResponseOK()) {
                Thread.sleep(pullResultsSleepIntervalMS);
            } else {
                response = result.getResponseAsJSONObject();
                // Wheh asked too quickly, we can get a 200 OK with a null answer.
                // In this case, response.getString("answer") throws an error
                String answer = response.optString("answer", null);
                gotIt = StringUtils.isNoneBlank(answer);
                if (!gotIt) {
                    Thread.sleep(pullResultsSleepIntervalMS);
                }
            }

        } while (!gotIt && count < pullResultsMaxTries);

        return result;
    }

    @Override
    public ServiceCallResult askQuestionAndGetAnswer(String configName, String agentId, String question,
            List<String> contextObjectIds, String extraPayloadJsonStr, Map<String, String> extraHeaders)
            throws InterruptedException {

        ServiceCallResult result = null;

        // 1. Ask the question
        result = askQuestion(configName, agentId, question, contextObjectIds, extraPayloadJsonStr, extraHeaders);
        if (result.getResponseCode() != 202) {
            return result;
        }
        JSONObject response = result.getResponseAsJSONObject();
        String questionId = response.getString("questionId");

        // 2. Pull the answer
        result = getAnswer(configName, questionId, extraHeaders);

        return result;
    }

    // ======================================================================
    // ======================================================================
    // Service Configuration
    // ======================================================================
    // ======================================================================
    @Override
    public List<String> getKDContribNames() {
        
        if (kdContribs == null) {
            kdContribs = new HashMap<String, KDDescriptor>();
        }
        
        return new ArrayList<>(kdContribs.keySet());
        
    }
    
    @Override
    public KDDescriptor getKDDescriptor(String configName) {
        
        if (StringUtils.isBlank(configName)) {
            configName = CONFIG_DEFAULT;
        }
        
        return kdContribs.get(configName);
    }
    
    /**
     * Component activated notification.
     * Called when the component is activated. All component dependencies are resolved at that moment.
     * Use this method to initialize the component.
     *
     * @param context the component context.
     */
    @Override
    public void activate(ComponentContext context) {
        super.activate(context);
        //log.warn("activate component");
    }

    /**
     * Component deactivated notification.
     * Called before a component is unregistered.
     * Use this method to do cleanup if any and free any resources held by the component.
     *
     * @param context the component context.
     */
    @Override
    public void deactivate(ComponentContext context) {
        super.deactivate(context);
        //log.warn("deactivate component");
    }

    /**
     * Registers the given extension.
     *
     * @param extension the extension to register
     */
    @Override
    public void registerExtension(Extension extension) {
        super.registerExtension(extension);
        
        if (kdContribs == null) {
            kdContribs = new HashMap<String, KDDescriptor>();
        }

        if (EXT_POINT_KD.equals(extension.getExtensionPoint())) {
            Object[] contribs = extension.getContributions();
            if (contribs != null) {
                for (Object contrib : contribs) {
                    KDDescriptor desc = (KDDescriptor) contrib;
                    kdContribs.put(desc.getName(), desc);
                }
            }
        }
    }

    /**
     * Unregisters the given extension.
     *
     * @param extension the extension to unregister
     */
    @Override
    public void unregisterExtension(Extension extension) {
        super.unregisterExtension(extension);
        
        if (kdContribs == null) {
            return;
        }
        
        if (EXT_POINT_KD.equals(extension.getExtensionPoint())) {
            Object[] contribs = extension.getContributions();
            if (contribs != null) {
                for (Object contrib : contribs) {
                    KDDescriptor desc = (KDDescriptor) contrib;
                    kdContribs.remove(desc.getName());
                }
            }
        }
    }

    /**
     * Start the component. This method is called after all the components were resolved and activated
     *
     * @param context the component context. Use it to get the current bundle context
     */
    @Override
    public void start(ComponentContext context) {
        //log.warn("Start component");

        // OK, all extensions loaded, let's initialize the auth. tokens
        if (kdContribs == null) {
            log.error("No configuration found for Knowledge Discovery. Calls, if any, will fail.");
        } else {
            discoveryAuthTokens = new HashMap<String, AuthenticationToken>();
            for (Map.Entry<String, KDDescriptor> entry : kdContribs.entrySet()) {
                KDDescriptor desc = entry.getValue();
                AuthenticationToken token = new AuthenticationTokenDiscovery(
                        desc.getAuthenticationBaseUrl() + AUTH_ENDPOINT, desc.getAuthenticationTokenParams());
                discoveryAuthTokens.put(desc.getName(), token);

                desc.checkConfigAndLogErrors();
            }
        }
    }

    /**
     * Stop the component.
     *
     * @param context the component context. Use it to get the current bundle context
     * @throws InterruptedException
     */
    @Override
    public void stop(ComponentContext context) throws InterruptedException {

        //log.warn("Stop component");
    }
}
