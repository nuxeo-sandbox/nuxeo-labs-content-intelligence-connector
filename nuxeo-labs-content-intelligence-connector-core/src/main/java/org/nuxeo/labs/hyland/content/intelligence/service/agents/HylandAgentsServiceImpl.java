package org.nuxeo.labs.hyland.content.intelligence.service.agents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.labs.hyland.content.intelligence.AuthenticationToken;
import org.nuxeo.labs.hyland.content.intelligence.AuthenticationTokenAgents;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCall;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.labs.hyland.content.intelligence.service.discovery.KDDescriptor;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.HylandKEServiceImpl;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.model.Extension;

public class HylandAgentsServiceImpl extends DefaultComponent implements HylandAgentsService {

    private static final Logger log = LogManager.getLogger(HylandAgentsServiceImpl.class);

    // Will add "/connect/token" to this endpoint.
    public static final String AUTH_BASE_URL_PARAM = HylandKEServiceImpl.AUTH_BASE_URL_PARAM;

    public static final String AUTH_ENDPOINT = HylandKEServiceImpl.AUTH_ENDPOINT;

    public static final String AGENTS_CLIENT_ID_PARAM = "nuxeo.hyland.cic.agents.clientId";

    public static final String AGENTS_CLIENT_SECRET_PARAM = "nuxeo.hyland.cic.agents.clientSecret";

    public static final String AGENTS_BASE_URL_PARAM = "nuxeo.hyland.cic.agents.baseUrl";

    public static final String AGENTS_ENVIRONMENT_PARAM = "nuxeo.hyland.cic.agents.environment";

    protected static Map<String, AuthenticationToken> agentsAuthTokens = null;

    protected static ServiceCall serviceCall = new ServiceCall();

    // ====================> Extensions points
    protected static final String EXT_POINT_AGENT = "agent";

    protected Map<String, AgentDescriptor> agentContribs = new HashMap<String, AgentDescriptor>();

    public static final String CONFIG_DEFAULT = "default";

    // ====================> Utils
    protected AgentDescriptor getDescriptor(String configName) {

        if (StringUtils.isBlank(configName)) {
            configName = CONFIG_DEFAULT;
        }

        return agentContribs.get(configName);
    }

    protected String getToken(String configName) {

        if (StringUtils.isBlank(configName)) {
            configName = CONFIG_DEFAULT;
        }

        AuthenticationToken token = agentsAuthTokens.get(configName);

        return token.getToken();
    }

    // ====================> Implementation
    @Override
    public ServiceCallResult getAllAgents(String configName, Map<String, String> extraHeaders) {

        ServiceCallResult result = null;

        // Get auth token
        String bearer = getToken(configName);
        if (StringUtils.isBlank(bearer)) {
            throw new NuxeoException("No authentication info for calling the Agents Builder service.");
        }

        AgentDescriptor config = getDescriptor(configName);
        String targetUrl = config.getBaseUrl();
        targetUrl += "/v1/agents";

        // Headers
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Accept", "*/*");
        headers.put("Authorization", "Bearer " + bearer);
        headers.put("Content-Type", "application/json");

        // Extra headers
        if (extraHeaders != null && extraHeaders.size() > 0) {
            for (String headerName : extraHeaders.keySet()) {
                headers.put(headerName, extraHeaders.get(headerName));
            }
        }

        result = serviceCall.get(targetUrl, headers);

        return result;
    }

    @Override
    public ServiceCallResult lookupAgent(String configName, String agentId, String versionId,
            Map<String, String> extraHeaders) {

        ServiceCallResult result = null;

        // Get auth token
        String bearer = getToken(configName);
        if (StringUtils.isBlank(bearer)) {
            throw new NuxeoException("No authentication info for calling the Agents Builder service.");
        }

        if (StringUtils.isBlank(versionId)) {
            versionId = "latest";
        }

        AgentDescriptor config = getDescriptor(configName);
        String targetUrl = config.getBaseUrl();
        targetUrl += "/v1/agents/" + agentId + "/versions/" + versionId;

        // Headers
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Accept", "*/*");
        headers.put("Authorization", "Bearer " + bearer);
        headers.put("Content-Type", "application/json");

        // Extra headers
        if (extraHeaders != null && extraHeaders.size() > 0) {
            for (String headerName : extraHeaders.keySet()) {
                headers.put(headerName, extraHeaders.get(headerName));
            }
        }

        result = serviceCall.get(targetUrl, headers);

        return result;

    }

    @Override
    public ServiceCallResult invokeTask(String configName, String agentId, String versionId, String payloadJsonStr,
            Map<String, String> extraHeaders) {

        ServiceCallResult result = null;

        // Get auth token
        String bearer = getToken(configName);
        if (StringUtils.isBlank(bearer)) {
            throw new NuxeoException("No authentication info for calling the Agents Builder service.");
        }

        if (StringUtils.isBlank(versionId)) {
            versionId = "latest";
        }

        AgentDescriptor config = getDescriptor(configName);
        String targetUrl = config.getBaseUrl();
        targetUrl += "/v1/agents/" + agentId + "/versions/" + versionId + "/invoke-task";

        // Headers
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Accept", "*/*");
        headers.put("Authorization", "Bearer " + bearer);
        headers.put("Content-Type", "application/json");

        // Extra headers
        if (extraHeaders != null && extraHeaders.size() > 0) {
            for (String headerName : extraHeaders.keySet()) {
                headers.put(headerName, extraHeaders.get(headerName));
            }
        }

        result = serviceCall.post(targetUrl, headers, payloadJsonStr);

        return result;
    }

    // ====================> Service
    @Override
    public List<String> getAgentContribNames() {

        if (agentContribs == null) {
            agentContribs = new HashMap<String, AgentDescriptor>();
        }

        return new ArrayList<>(agentContribs.keySet());

    }

    @Override
    public AgentDescriptor getAgentDescriptor(String configName) {

        if (StringUtils.isBlank(configName)) {
            configName = CONFIG_DEFAULT;
        }

        return agentContribs.get(configName);
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
        // log.warn("activate component");
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
        // log.warn("deactivate component");
    }

    /**
     * Registers the given extension.
     *
     * @param extension the extension to register
     */
    @Override
    public void registerExtension(Extension extension) {
        super.registerExtension(extension);

        if (agentContribs == null) {
            agentContribs = new HashMap<String, AgentDescriptor>();
        }

        if (EXT_POINT_AGENT.equals(extension.getExtensionPoint())) {
            Object[] contribs = extension.getContributions();
            if (contribs != null) {
                for (Object contrib : contribs) {
                    AgentDescriptor desc = (AgentDescriptor) contrib;
                    agentContribs.put(desc.getName(), desc);
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

        if (agentContribs == null) {
            return;
        }

        if (EXT_POINT_AGENT.equals(extension.getExtensionPoint())) {
            Object[] contribs = extension.getContributions();
            if (contribs != null) {
                for (Object contrib : contribs) {
                    KDDescriptor desc = (KDDescriptor) contrib;
                    agentContribs.remove(desc.getName());
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
        // log.warn("Start component");

        // OK, all extensions loaded, let's initialize the auth. tokens
        if (agentContribs == null) {
            log.error("No configuration found for Agents Service. Calls, if any, will fail.");
        } else {
            agentsAuthTokens = new HashMap<String, AuthenticationToken>();
            for (Map.Entry<String, AgentDescriptor> entry : agentContribs.entrySet()) {
                AgentDescriptor desc = entry.getValue();
                AuthenticationToken token = new AuthenticationTokenAgents(
                        desc.getAuthenticationBaseUrl() + AUTH_ENDPOINT, desc.getAuthenticationTokenParams());
                agentsAuthTokens.put(desc.getName(), token);

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

        // log.warn("Stop component");
    }

}
