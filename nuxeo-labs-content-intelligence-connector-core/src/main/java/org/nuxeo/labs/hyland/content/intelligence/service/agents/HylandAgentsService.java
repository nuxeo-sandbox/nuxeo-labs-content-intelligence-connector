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
    List<String> getContribNames();

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

    /**
     * Return a simplified response with only sopme fields.
     * If it fails building the simplified response, it returns the original object in "originalResponse" and adds
     * "simplifiedProcessingSuccess": false.
     * <br>
     * Even if there are several "output" and inside an output, several "content", we return only the first one.
     * If there are several outputs or several content in first answer, "moreResults" is set to true, and
     * "originalResponse" is, well, the original response, unchanged.
     * <br>
     * Example #1:
     * 
     * <pre>{@code
     * {
     *   "simplifiedProcessingSuccess": true,
     *   "text": "the first answer",
     *   "sources": [
     *     {
     *       "objectId": "abc-def-etc.",
     *       "score": 0.67
     *     },
     *     {
     *       "objectId": "123-456-etc.",
     *       "score": 0.83
     *     },
     *     . . .
     *   ],
     *   "moreResult": false
     * }
     * }</pre>
     * 
     * Example #2, more resilts available.
     * 
     * <pre>{@code
     * {
     *   "simplifiedProcessingSuccess": true,
     *   "text": "the first answer",
     *   "sources": [
     *     {
     *       "objectId": "abc-def-etc.",
     *       "score": 0.67
     *     },
     *     {
     *       "objectId": "123-456-etc.",
     *       "score": 0.83
     *     },
     *     . . .
     *   ],
     *   "moreResult": true,
     *   "originalResponse": the original response
     * }
     * }</pre>
     * 
     * As of FEB 2026, result from the service is:
     * 
     * <pre>{@code
     * {
     *   "createdAt": 1770917608,
     *    "model": "meta.llama4-scout-17b-instruct-v1:0",
     *    "object": "response",
     *    "output": [
     *      {
     *        "type": "message",
     *        "status": "completed",
     *        "content": [
     *          {
     *            "type": "output_text",
     *            "text": "#### I don't have enough information to answer this question"
     *          }
     *        ],
     *        "role": "assistant"
     *      }
     *    ],
     *    "customOutputs": {  ==> OPTIONAL
     *      "sourceNodes": [
     *        {
     *          "docId": "094f72b1-1762-4995-ba2f-2fd3804c29b2__a47aa670-27d7-4b3b-8b8f-74f96849c38b",
     *          "chunkId": "581ff1a2-a0be-41b4-9bd7-57f22046e020",
     *          "score": 0.032786883,
     *          "text": "Here text found by the suste to return the answer."
     *        },
     *        . . . more nodes . . .
     *      ],
     *      "ragMode": "normal"
     *    }
     * }
     * }</pre>
     * 
     * objectId is the 2 part of the "docId" (and usually is the Nuxeo doc UUID)
     * 
     * @param response
     * @return
     * @since TODO
     */
    static JSONObject simplifyResponse(JSONObject response) {

        JSONObject simplified;
        boolean hasMoreResults = false;
        try {
            // Get the answer
            JSONArray output = response.getJSONArray("output");
            hasMoreResults = output.length() > 1;
            JSONObject firstOutput = output.getJSONObject(0);
            JSONArray content = firstOutput.getJSONArray("content");
            hasMoreResults = hasMoreResults || content.length() > 1;
            String text = content.getJSONObject(0).getString("text");

            simplified = new JSONObject();
            simplified.put("text", text);

            // Get the sources
            JSONArray sources = new JSONArray();
            if (response.has("customOutputs")) {
                JSONObject customOutputs = response.getJSONObject("customOutputs");
                JSONArray sourceNodes = customOutputs.getJSONArray("sourceNodes");

                for (int i = 0; i < sourceNodes.length(); i++) {

                    JSONObject value = sourceNodes.getJSONObject(i);

                    JSONObject oneSource = new JSONObject();
                    String objectId = value.getString("docId");
                    int index = objectId.indexOf("__");
                    if (index != -1 && index + 2 < objectId.length()) {
                        objectId = objectId.substring(index + 2);
                    }
                    oneSource.put("objectId", objectId);
                    oneSource.put("score", value.getDouble("score"));
                    sources.put(oneSource);
                }
            }
            simplified.put("sources", sources);
            simplified.put("simplifiedProcessingSuccess", true);
            simplified.put("moreResults", hasMoreResults);
            if (hasMoreResults) {
                simplified.put("originalResponse", response);
            }

        } catch (Exception e) {
            simplified = new JSONObject();
            simplified.put("simplifiedProcessingSuccess", false);
            simplified.put("originalResponse", response);
        }
        return simplified;
    }

}
