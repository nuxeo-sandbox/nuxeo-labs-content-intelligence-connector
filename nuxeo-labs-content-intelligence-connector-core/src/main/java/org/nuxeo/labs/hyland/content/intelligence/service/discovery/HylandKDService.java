package org.nuxeo.labs.hyland.content.intelligence.service.discovery;

import java.util.List;
import java.util.Map;

import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;

public interface HylandKDService {
    
    public static final String SERVICE_LABEL = "Knowledge Discovery";

    /*
     * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
     * Main calls to KD
     * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
     */

    /**
     * Main call to KD, allowing for flexibility, typically when the API gralmmar changes (json payload changes, json
     * return changes)
     * extraHeaders, if not null, will override any header previously set by the method.
     * <br>
     * configName is the contribution to read for authentication and misc. If null or "", we use "default" (contributed
     * by the plugin and using config. parameters)
     * 
     * @param configName
     * @param httpMethod
     * @param endpoint
     * @param jsonPayload
     * @param extraHeaders
     * @return the result of the call
     * @since 2023
     */
    public ServiceCallResult invokeDiscovery(String configName, String httpMethod, String endpoint, String jsonPayload,
            Map<String, String> extraHeaders);

    /**
     * Convenience method when no extra header is required. See
     * <code>ServiceCallResult invokeDiscovery(String httpMethod, String endpoint, String jsonPayload, Map<String, String> extraHeaders)</code>
     * <br>
     * configName is the contribution to read for authentication and misc. If null or "", we use "default" (contributed
     * by the plugin and using config. parameters)
     * 
     * @param configName
     * @param httpMethod
     * @param endpoint
     * @param jsonPayload
     * @return
     * @since 2023
     */
    public ServiceCallResult invokeDiscovery(String configName, String httpMethod, String endpoint, String jsonPayload);

    /*
     * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
     * Helper methods. Should use config. params as much as possible.
     * May break when the API changes (or endpoints)
     * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
     */
    /**
     * extraHeaders is optional
     * <br>
     * configName is the contribution to read for authentication and misc. If null or "", we use "default" (contributed
     * by the plugin and using config. parameters)
     * 
     * @param configName
     * @param extraHeaders
     * @return
     * @since TODO
     */
    public ServiceCallResult getAllAgents(String configName, Map<String, String> extraHeaders);

    /**
     * Only question is required:
     * - agentId can be read from configiration
     * - contextObjectIds can be empty (or null)
     * - extraJsonPayload can be empty (or null), will be added to the payload.
     * It can contains "question" and "contextObjectIds" that will overrid the one passed as parameters.
     * - Extraheaders: Will override any header previously set. Can be empty/null
     * Returns the JSON result from the service. If succesfull (response code 202, "Accepted"), the result will have the
     * questionId and the answer cna then be pulled.
     * configName is the contribution to read for authentication and misc. If null or "", we use "default"
     * 
     * @param configName
     * @param agentId
     * @param question
     * @param contextObjectIds
     * @paral extraJsonPayload
     * @param extraHeaders
     * @return
     * @since 2023
     */
    public ServiceCallResult askQuestion(String configName, String agentId, String question,
            List<String> contextObjectIds, String extraPayloadJsonStr, Map<String, String> extraHeaders);

    /**
     * Pull the result. This can take teim, implementation should details the number of tries, the timeout, etc.
     * <br>
     * configName is the contribution to read for authentication and misc. If null or "", we use "default" (contributed
     * by the plugin and using config. parameters)
     * 
     * @param configName
     * @param questionId
     * @param extraHeaders
     * @return
     * @since 2023
     */
    public ServiceCallResult getAnswer(String configName, String questionId, Map<String, String> extraHeaders)
            throws InterruptedException;

    /**
     * Only question is required:
     * - agentId can be read from configuration
     * - contextObjectIds can be empty (or null)
     * - Extraheaders: Will override any header previously set. Can be empty/null
     * <br>
     * configName is the contribution to read for authentication and misc. If null or "", we use "default" (contributed
     * by the plugin and using config. parameters)
     * 
     * @param configName
     * @param agentId
     * @param question
     * @param contextObjectIds
     * @param extraHeaders
     * @return the response from the service
     * @since 2023
     */
    public ServiceCallResult askQuestionAndGetAnswer(String configName, String agentId, String question,
            List<String> contextObjectIds, String extraPayloadJsonStr, Map<String, String> extraHeaders)
            throws InterruptedException;
    
    /**
     * 
     * @return the list of contributions
     * @since 2023
     */
    public List<String> getContribNames();
    
    /**
     * Introspection
     */
    public KDDescriptor getKDDescriptor(String configName);

    /*
     * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
     * Conversation API
     * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
     */

    /**
     * Start a new conversation with an agent. Sends the initial question and returns both the
     * conversation metadata (including the conversationId) and the first message (question + answer).
     * Unlike single-shot questions, this endpoint returns the answer synchronously (no polling required).
     * <br>
     * configName is the contribution to read for authentication and misc. If null or "", we use "default".
     *
     * @param configName the configuration contribution name
     * @param agentId the agent to converse with. If blank, uses the default agentId from configuration.
     * @param question the initial question to ask
     * @param dynamicFilterJsonStr optional JSON string for dynamic filter (can be null/blank)
     * @param extraHeaders optional extra headers to override defaults (can be null)
     * @return the result containing { conversation: { id, lastModified }, message: { id, answer, ... } }
     * @since 2025
     */
    public ServiceCallResult startConversation(String configName, String agentId, String question,
            String dynamicFilterJsonStr, Map<String, String> extraHeaders);

    /**
     * Continue an existing conversation by sending a follow-up question. The agent uses previous messages
     * in the conversation as context. Returns the new message (question + answer) synchronously.
     * <br>
     * configName is the contribution to read for authentication and misc. If null or "", we use "default".
     *
     * @param configName the configuration contribution name
     * @param agentId the agent to converse with. If blank, uses the default agentId from configuration.
     * @param conversationId the conversation to continue
     * @param question the follow-up question to ask
     * @param dynamicFilterJsonStr optional JSON string for dynamic filter (can be null/blank)
     * @param extraHeaders optional extra headers to override defaults (can be null)
     * @return the result containing { id, answer, question, documentReferences, status, ... }
     * @since 2025
     */
    public ServiceCallResult continueConversation(String configName, String agentId, String conversationId,
            String question, String dynamicFilterJsonStr, Map<String, String> extraHeaders);

    /**
     * Submit feedback (Good, Bad, or Retry) for a specific message in a conversation.
     * <br>
     * configName is the contribution to read for authentication and misc. If null or "", we use "default".
     *
     * @param configName the configuration contribution name
     * @param agentId the agent that owns the conversation
     * @param conversationId the conversation containing the message
     * @param messageId the message to provide feedback on
     * @param feedback the feedback value: "Good", "Bad", or "Retry"
     * @param extraHeaders optional extra headers to override defaults (can be null)
     * @return the result of the feedback submission
     * @since 2025
     */
    public ServiceCallResult submitConversationFeedback(String configName, String agentId, String conversationId,
            String messageId, String feedback, Map<String, String> extraHeaders);

}
