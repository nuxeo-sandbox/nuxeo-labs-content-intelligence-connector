package org.nuxeo.labs.hyland.content.intelligence.service.discovery;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;

public interface HylandKDService {

    /*
     * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
     * Main calls to KD
     * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
     */

    /**
     * Main call to KD, allowing for flexibility, typically when the API gralmmar changes (json payload changes, json
     * return changes)
     * extraHeaders, if not null, will override any header previously set by the method.
     * 
     * @param httpMethod
     * @param endpoint
     * @param jsonPayload
     * @param extraHeaders
     * @return the result of the call
     * @since 2023
     */
    public ServiceCallResult invokeDiscovery(String httpMethod, String endpoint, String jsonPayload,
            Map<String, String> extraHeaders);

    /**
     * Convenience method when no extra header is required. See
     * <code>ServiceCallResult invokeDiscovery(String httpMethod, String endpoint, String jsonPayload, Map<String, String> extraHeaders)</code>
     * 
     * @param httpMethod
     * @param endpoint
     * @param jsonPayload
     * @return
     * @since 2023
     */
    public ServiceCallResult invokeDiscovery(String httpMethod, String endpoint, String jsonPayload);

    /*
     * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
     * Helper methods. Should use config. params as much as possible.
     * May break when the API changes (or endpoints)
     * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
     */
    /**
     * extraHeaders is optional
     * 
     * @param extraHeaders
     * @return
     * @since TODO
     */
    public ServiceCallResult getAllAgents(Map<String, String> extraHeaders);

    /**
     * Only question is required:
     * - agentId can be read from configiration
     * - contextObjectIds can be empty (or null)
     * - extraJsonPayload can be empty (or null), will be added to the payload.
     *   It can contains "question" and "contextObjectIds" that will overrid the one passed as parameters.
     * - Extraheaders: Will override any header previously set. Can be empty/null
     * Returns the JSON result from the service. If succesfull (response code 202, "Accepted"), the result will have the
     * questionId and the answer cna then be pulled.
     * 
     * @param agentId
     * @param question
     * @param contextObjectIds
     * @paral extraJsonPayload
     * @param extraHeaders
     * @return
     * @since 2023
     */
    public ServiceCallResult askQuestion(String agentId, String question, List<String> contextObjectIds,
            String extraPayloadJsonStr, Map<String, String> extraHeaders);

    /**
     * Pull the result. This can take teim, implementation should details the number of tries, the timeout, etc.
     * 
     * @param questionId
     * @param extraHeaders
     * @return
     * @since 2023
     */
    public ServiceCallResult getAnswer(String questionId, Map<String, String> extraHeaders) throws InterruptedException;

    /**
     * Only question is required:
     * - agentId can be read from configiration
     * - contextObjectIds cabn be empty (or null)
     * - Extraheaders: Will override any header previously set. Can be empty/null
     * 
     * @param agentId
     * @param question
     * @param contextObjectIds
     * @param extraHeaders
     * @return the response from the service
     * @since 2023
     */
    public ServiceCallResult askQuestionAndGetAnswer(String agentId, String question, List<String> contextObjectIds,
            String extraPayloadJsonStr, Map<String, String> extraHeaders) throws InterruptedException;

}
