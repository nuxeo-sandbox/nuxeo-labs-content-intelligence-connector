/*
 * (C) Copyright 2026 Hyland (http://hyland.com/)  and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Thibaud Arguillere
 */
package org.nuxeo.labs.hyland.content.intelligence.service.agents;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;

/**
 * @since TODO
 */
public interface HylandAgentsService {

    public enum AgentType {
        RAG, TASK, TOOL
    }

    /**
     * Get all agents in the account/app. referenced by configName.
     * configName is the contribution to read for authentication and misc. If null or "", we use "default" (contributed
     * by the plugin and using config. parameters)
     * 
     * @param configName
     * @param extraHeaders, optional
     * @return
     * @since TODO
     */
    public ServiceCallResult getAllAgents(String configName, Map<String, String> extraHeaders);

    /**
     * returns the full JSON after calling the service.
     * If versionId is null or empty, the latest version is returned.
     * configName is the contribution to read for authentication and misc. If null or "", we use "default" (contributed
     * by the plugin and using config. parameters)
     * 
     * @param configName
     * @param agentId
     * @param versionId
     * @param extraHeaders
     * @return
     * @since TODO
     */
    public ServiceCallResult lookupAgent(String configName, String agentId, String versionId,
            Map<String, String> extraHeaders);

    /**
     * Invoke the the agent agentId, using the endpoint available for the type of agent.
     * Pass in payloadJsonStr the expected input for the agent.
     * If versionId is null or empty, the latest version is invoked.
     * 
     * @param agentType, required
     * @param configName, optional
     * @param agentId, required
     * @param versionId, optional
     * @param payloadJsonStr, required
     * @param extraHeaders, optional
     * @return
     * @since TODO
     */
    public ServiceCallResult invokeAgent(AgentType agentType, String configName, String agentId, String versionId,
            String payloadJsonStr, Map<String, String> extraHeaders);

    /**
     * @return the list of contributions
     * @since 2023
     */
    List<String> getAgentContribNames();

    /**
     * Introspection
     */
    AgentDescriptor getAgentDescriptor(String configName);

    /**
     * Since FEB 2026, KD agent can be called via Agent Byilder RAG agents, but the JSON payload is not the same.
     * This utility centralize the formating (for easier migration)
     * AT the time this is writte, format is:
     * (see
     * https://hyland.github.io/ContentIntelligence-Docs/AgentBuilderPlatform/AgentBuilderAPI/invoke-agent-v-1-agents-agent-id-versions-version-id-invoke-post)
     * 
     * <pre>{@code
     * {
     *   "messages": [ {"role": "user", "content": "the question"} ],
     *   "hxqlQuery": "the hxqlQuery for the context",                   - OPTIONAL
     *   "guardrails": ["oneGuardRail", "another one"],                  - OPTIONAL
     *   "hybridSearch": Not used in this context of KD question/answer  - OPTIONAL
     * }
     * }</pre>
     * 
     * @param question
     * @param contextObjectIdsJsonArrayStr. optional
     * @return a JSON paylod ready to be sent to Agent Builder for this KD purpose
     * @since TODO
     */
    static JSONObject formatJsonPayloadForKDQuestion(String question, String contextObjectIdsJsonArrayStr,
            String guardrailsJsonArrayStr) {

        // Question
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", question);

        JSONArray messages = new JSONArray();
        messages.put(message);

        // Context
        String hxqlQuery = null;
        if (StringUtils.isNotBlank(contextObjectIdsJsonArrayStr)) {
            JSONArray contextObjectIds = new JSONArray(contextObjectIdsJsonArrayStr);
            if (contextObjectIds.length() == 1) {
                hxqlQuery = "SELECT * FROM SysContent WHERE cin_id = '" + contextObjectIds.getString(0) + "'";
            } else {
                String ids = IntStream.range(0, contextObjectIds.length())
                                      .mapToObj(i -> "'" + contextObjectIds.getString(i) + "'")
                                      .collect(Collectors.joining(", "));
                hxqlQuery = "SELECT * FROM SysContent WHERE cin_id IN (" + ids + ")";
            }
        }

        // Guardrails
        JSONArray guardrails = null;
        if (StringUtils.isNotBlank(guardrailsJsonArrayStr)) {
            guardrails = new JSONArray(guardrailsJsonArrayStr);
        }

        JSONObject payload = new JSONObject();
        payload.put("messages", messages);
        if (hxqlQuery != null) {
            payload.put("hxqlQuery", hxqlQuery);
        }
        if (guardrails != null) {
            payload.put("guardrails", guardrails);
        }

        return payload;
    }

}
