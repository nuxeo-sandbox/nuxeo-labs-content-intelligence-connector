package org.nuxeo.labs.hyland.content.intelligence.service.discovery;

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
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.HylandKEServiceImpl;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.model.Extension;

/**
 * This class handles calls to Content Inteligence Knowledge Discovery service
 * 
 * @since 2023
 */
public class HylandKDServiceImpl extends DefaultComponent implements HylandKDService {

    private static final Logger log = LogManager.getLogger(HylandKDServiceImpl.class);

    // Will add "/connect/token" to this endpoint.
    public static final String AUTH_BASE_URL_PARAM = HylandKEServiceImpl.AUTH_BASE_URL_PARAM;

    public static final String DISCOVERY_CLIENT_ID_PARAM = "nuxeo.hyland.cic.discovery.clientId";

    public static final String DISCOVERY_CLIENT_SECRET_PARAM = "nuxeo.hyland.cic.discovery.clientSecret";

    public static final String DISCOVERY_BASE_URL_PARAM = "nuxeo.hyland.cic.discovery.baseUrl";

    public static final String DISCOVERY_ENVIRONMENT_PARAM = "nuxeo.hyland.cic.discovery.environment";

    public static final String DISCOVERY_DEFAULT_SOURCEID_PARAM = "nuxeo.hyland.cic.discovery.default.sourceId";

    public static final String DISCOVERY_DEFAULT_AGENTID_PARAM = "nuxeo.hyland.cic.discovery.default.agentId";

    protected static AuthenticationToken discoveryAuthToken;

    protected static String authBaseUrl = null;

    protected static String authFullUrl;

    protected static String discoveryBaseUrl;

    protected static String clientId = null;

    protected static String clientSecret = null;

    protected static String discoveryEnvironment;

    protected static String defaultSourceId;

    protected static String defaultAgentId;

    protected static ServiceCall serviceCall = new ServiceCall();

    public HylandKDServiceImpl() {
        initialize();
    }

    protected void initialize() {

        // ==========> Auth
        authBaseUrl = Framework.getProperty(AUTH_BASE_URL_PARAM);
        discoveryEnvironment = Framework.getProperty(DISCOVERY_ENVIRONMENT_PARAM);

        // ==========> EndPoints
        discoveryBaseUrl = Framework.getProperty(DISCOVERY_BASE_URL_PARAM);

        // ==========> Clients
        clientId = Framework.getProperty(DISCOVERY_CLIENT_ID_PARAM);
        clientSecret = Framework.getProperty(DISCOVERY_CLIENT_SECRET_PARAM);

        // ==========> SanityChecks
        if (StringUtils.isBlank(authBaseUrl)) {
            log.warn("No CIC Authentication Base URL provided (" + AUTH_BASE_URL_PARAM
                    + "), calls to the service will fail.");
            authBaseUrl = ""; // avoid null for setting authFullUrl
        }
        authFullUrl = authBaseUrl + "/connect/token";

        if (StringUtils.isBlank(discoveryBaseUrl)) {
            log.warn("No CIC Data Curation endpoint provided (" + DISCOVERY_BASE_URL_PARAM
                    + "), calls to the service will fail.");
        } else if (discoveryBaseUrl.endsWith("/")) {
            discoveryBaseUrl = discoveryBaseUrl.substring(0, discoveryBaseUrl.length() - 1);
        }

        if (StringUtils.isBlank(clientId)) {
            log.warn("No CIC Discovery ClientId provided (" + DISCOVERY_CLIENT_ID_PARAM
                    + "), calls to the service will fail.");
        }

        if (StringUtils.isBlank(clientSecret)) {
            log.warn("No CIC Discovery ClientSecret provided (" + DISCOVERY_CLIENT_SECRET_PARAM
                    + "), calls to the service will fail.");
        }

        if (StringUtils.isBlank(discoveryEnvironment)) {
            log.warn("No CIC Discovery Environement provided (" + DISCOVERY_ENVIRONMENT_PARAM
                    + "), calls to the service will fail.");
        }

        // ==========> Prepare for getting auth. tokens
        discoveryAuthToken = new AuthenticationTokenDiscovery(authFullUrl, clientId, clientSecret,
                discoveryEnvironment);

        // ==========> Misc Optional Parameters
        defaultSourceId = Framework.getProperty(DISCOVERY_DEFAULT_SOURCEID_PARAM);
        defaultAgentId = Framework.getProperty(DISCOVERY_DEFAULT_AGENTID_PARAM);

    }

    @Override
    public ServiceCallResult invokeDiscovery(String httpMethod, String endpoint, String jsonPayload) {
        return invokeDiscovery(httpMethod, endpoint, jsonPayload, null);
    }

    @Override
    public ServiceCallResult invokeDiscovery(String httpMethod, String endpoint, String jsonPayload,
            Map<String, String> extraHeaders) {

        ServiceCallResult result = null;

        // Get auth token
        String bearer = discoveryAuthToken.getToken();
        if (StringUtils.isBlank(bearer)) {
            throw new NuxeoException("No authentication info for calling the Enrichment service.");
        }

        // URL/endpoint
        String targetUrl = discoveryBaseUrl;
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
        headers.put("Hxp-Environment", discoveryEnvironment);
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
    public ServiceCallResult getAllAgents(Map<String, String> extraHeaders) {

        ServiceCallResult result = invokeDiscovery("GET", "/agent/agents", null, extraHeaders);

        return result;
    }

    @Override
    public ServiceCallResult askQuestion(String agentId, String question, List<String> contextObjectIds,
            String extraPayloadJsonStr, Map<String, String> extraHeaders) {

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

        result = invokeDiscovery("POST", endPoint, payload.toString(), extraHeaders);

        return result;
    }

    @Override
    public ServiceCallResult getAnswer(String questionId, Map<String, String> extraHeaders)
            throws InterruptedException {

        ServiceCallResult result = null;

        // Get the answer. This is a loop-pull. For now, we hard code the tries
        String endPoint = "/qna/questions/" + questionId + "/answer";
        int count = 0;
        int MAXTRIES = 10;
        JSONObject response;
        boolean gotIt = false;
        do {
            count += 1;
            if (count > 2) {
                log.info("getAnswer(), trying to get an answer to question ID " + questionId + ", call " + count + "/"
                        + MAXTRIES + ".");
            } else if (count > 9) {
                log.warn("getAnswer(), trying to get an answer to question ID " + questionId + ", call " + count + "/"
                        + MAXTRIES + ".");
            }

            result = invokeDiscovery("GET", endPoint, null, extraHeaders);
            if (!result.callResponseOK()) {
                Thread.sleep(3000);
            } else {
                response = result.getResponseAsJSONObject();
                // Wheh asked too quickly, we can get a 200 OK with a null answer.
                // In this case, response.getString("answer") throws an error
                String answer = response.optString("answer", null);
                gotIt = StringUtils.isNoneBlank(answer);
                if (!gotIt) {
                    Thread.sleep(3000);
                }
            }

        } while (!gotIt && count < MAXTRIES);

        return result;
    }

    @Override
    public ServiceCallResult askQuestionAndGetAnswer(String agentId, String question, List<String> contextObjectIds,
            String extraPayloadJsonStr, Map<String, String> extraHeaders) throws InterruptedException {

        ServiceCallResult result = null;
        
        // 1. Ask the question
        result = askQuestion(agentId, question, contextObjectIds, extraPayloadJsonStr, extraHeaders);
        if (result.getResponseCode() != 202) {
            return result;
        }
        JSONObject response = result.getResponseAsJSONObject();
        String questionId = response.getString("questionId");
        
        
        // 2. Pull the answer
        result = getAnswer(questionId, extraHeaders);

        return result;
    }

    // ======================================================================
    // ======================================================================
    // We don't have XML config (for now :-))
    // ======================================================================
    // ======================================================================
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
    }

    /**
     * Registers the given extension.
     *
     * @param extension the extension to register
     */
    @Override
    public void registerExtension(Extension extension) {
        super.registerExtension(extension);
    }

    /**
     * Unregisters the given extension.
     *
     * @param extension the extension to unregister
     */
    @Override
    public void unregisterExtension(Extension extension) {
        super.unregisterExtension(extension);
    }

    /**
     * Start the component. This method is called after all the components were resolved and activated
     *
     * @param context the component context. Use it to get the current bundle context
     */
    @Override
    public void start(ComponentContext context) {
        // do nothing by default. You can remove this method if not used.
    }

    /**
     * Stop the component.
     *
     * @param context the component context. Use it to get the current bundle context
     * @throws InterruptedException
     */
    @Override
    public void stop(ComponentContext context) throws InterruptedException {
        // do nothing by default. You can remove this method if not used.
    }
}
