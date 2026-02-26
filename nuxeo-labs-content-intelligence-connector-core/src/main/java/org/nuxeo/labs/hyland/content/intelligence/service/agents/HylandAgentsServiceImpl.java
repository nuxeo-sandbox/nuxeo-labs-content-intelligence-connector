package org.nuxeo.labs.hyland.content.intelligence.service.agents;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.labs.hyland.content.intelligence.authentication.AuthenticationToken;
import org.nuxeo.labs.hyland.content.intelligence.authentication.AuthenticationTokenAgents;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCall;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.labs.hyland.content.intelligence.service.AbstractCICServiceComponent;
import org.nuxeo.labs.hyland.content.intelligence.service.CICServiceConstants;
import org.nuxeo.runtime.model.ComponentContext;

public class HylandAgentsServiceImpl extends AbstractCICServiceComponent<AgentDescriptor> implements HylandAgentsService {

    public static final String AGENTS_CLIENT_ID_PARAM = "nuxeo.hyland.cic.agents.clientId";

    public static final String AGENTS_CLIENT_SECRET_PARAM = "nuxeo.hyland.cic.agents.clientSecret";

    public static final String AGENTS_BASE_URL_PARAM = "nuxeo.hyland.cic.agents.baseUrl";

    public static final String AGENTS_ENVIRONMENT_PARAM = "nuxeo.hyland.cic.agents.environment";

    protected static Map<String, AuthenticationToken> agentsAuthTokens = null;

    protected static ServiceCall serviceCall = new ServiceCall();

    // ====================> Extensions points
    protected static final String EXT_POINT_AGENT = "agent";

    // ====================> Utils
    protected AgentDescriptor getDescriptor(String configName) {
        return super.getDescriptor(configName);
    }

    protected String getToken(String configName) {
        return super.getToken(agentsAuthTokens, configName);
    }

    @Override
    protected String getDescriptorExtensionPoint() {
        return EXT_POINT_AGENT;
    }

    @Override
    protected String getServiceLabel() {
        return "Agents Service";
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
    public ServiceCallResult invokeAgent(AgentType agentType, String configName, String agentId, String versionId,
            String payloadJsonStr, Map<String, String> extraHeaders) {

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
        switch (agentType) {
        case RAG:
            targetUrl += "/v1/agents/" + agentId + "/versions/" + versionId + "/invoke";
            break;

        case TASK:
            targetUrl += "/v1/agents/" + agentId + "/versions/" + versionId + "/invoke-task";
            break;

        case TOOL:
            targetUrl += "/v1/agents/" + agentId + "/versions/" + versionId + "/invoke";
            break;
        }

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
    public List<String> getContribNames() {
        return super.getContribNames();
    }

    @Override
    public AgentDescriptor getAgentDescriptor(String configName) {
        return super.getDescriptor(configName);
    }

    /**
     * Start the component. This method is called after all the components were resolved and activated
     *
     * @param context the component context. Use it to get the current bundle context
     */
    @Override
    public void start(ComponentContext context) {
        
        agentsAuthTokens = initAuthTokens(
                desc -> new AuthenticationTokenAgents(
                        desc.getAuthenticationBaseUrl() + CICServiceConstants.AUTH_ENDPOINT,
                desc.getAuthenticationTokenParams()));
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
