/*
 * (C) Copyright 2025 Hyland (http://hyland.com/) and others.
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
package org.nuxeo.labs.hyland.content.intelligence.automation.agents;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;

/**
 * Returns the locally registered Agentic agents (i.e. {@code CICAgenticAgentAndConfig} documents) the current user can
 * READ, ordered by title. Java port of the Studio scripted operation {@code Agentic_GetAgents}, minus the
 * {@code Auth.LoginAs}/{@code Auth.Logout} pair: visibility is controlled by Nuxeo permissions on the documents
 * themselves (admins grant READ to the right groups).
 * <p>
 * Output is a JSON {@link Blob} holding a plain array (NOT the canonical {@code {responseCode, response, ...}}
 * envelope used by ops that wrap a CIC HTTP call), each entry being:
 *
 * <pre>
 * {
 *   "agent":       "&lt;dc:title&gt;",
 *   "agentId":     "&lt;cicagenticagentandconfig:agentId&gt;",
 *   "configName":  "&lt;cicagenticagentandconfig:configName&gt;",
 *   "description": "&lt;dc:description&gt;",
 *   "inputs":      { ... }, // parsed from cicagenticagentandconfig:inputSchemaJson (blank =&gt; {})
 *   "outputs":     { ... }  // tools[0].outputSchema.properties (blank or malformed =&gt; {})
 * }
 * </pre>
 *
 * Intended for Web UI elements that need to populate an agentic-agent picker.
 *
 * @since 2023.21
 */
@Operation(id = HylandAgentsAvailableAgenticAgentsOp.ID, category = "Hyland Content Intelligence", label = "Get Available Agentic Agents (Local)", description = ""
        + "Returns a JSON Blob holding a plain array of the locally registered CICAgenticAgentAndConfig"
        + " documents the current user has READ access to. Each entry is"
        + " {agent, agentId, configName, description, inputs, outputs}, where inputs is the parsed"
        + " inputSchemaJson and outputs is tools[0].outputSchema.properties parsed from toolsJson."
        + " Java port of the Studio scripted operation Agentic_GetAgents.")
public class HylandAgentsAvailableAgenticAgentsOp {

    public static final String ID = "HylandAgents.AvailableAgenticAgents";

    private static final Logger log = LogManager.getLogger(HylandAgentsAvailableAgenticAgentsOp.class);

    protected static final String NXQL = "SELECT * FROM CICAgenticAgentAndConfig"
            + " WHERE ecm:isVersion = 0 AND ecm:isProxy = 0 AND ecm:isTrashed = 0"
            + " ORDER BY dc:title";

    protected static final String XPATH_AGENT_ID = "cicagenticagentandconfig:agentId";

    protected static final String XPATH_CONFIG_NAME = "cicagenticagentandconfig:configName";

    protected static final String XPATH_INPUT_SCHEMA_JSON = "cicagenticagentandconfig:inputSchemaJson";

    protected static final String XPATH_TOOLS_JSON = "cicagenticagentandconfig:toolsJson";

    @Context
    protected CoreSession session;

    @OperationMethod
    public Blob run() {

        var docs = session.query(NXQL);
        var agents = new JSONArray();

        for (DocumentModel doc : docs) {
            var entry = new JSONObject();
            entry.put("agent", doc.getTitle());
            entry.put("agentId", (String) doc.getPropertyValue(XPATH_AGENT_ID));
            entry.put("configName", (String) doc.getPropertyValue(XPATH_CONFIG_NAME));
            entry.put("description", (String) doc.getPropertyValue("dc:description"));

            // inputs <- inputSchemaJson (blank => {})
            JSONObject inputs = new JSONObject();
            String inputSchemaJson = (String) doc.getPropertyValue(XPATH_INPUT_SCHEMA_JSON);
            if (inputSchemaJson != null && !inputSchemaJson.isBlank()) {
                try {
                    inputs = new JSONObject(inputSchemaJson);
                } catch (JSONException e) {
                    log.warn("Failed to parse inputSchemaJson on doc id={}: {}", doc.getId(), e.getMessage());
                }
            }
            entry.put("inputs", inputs);

            // outputs <- tools[0].outputSchema.properties (blank or malformed => {})
            JSONObject outputs = new JSONObject();
            String toolsJson = (String) doc.getPropertyValue(XPATH_TOOLS_JSON);
            if (toolsJson != null && !toolsJson.isBlank()) {
                try {
                    var tools = new JSONArray(toolsJson);
                    var props = tools.getJSONObject(0).getJSONObject("outputSchema").getJSONObject("properties");
                    outputs = props;
                } catch (JSONException e) {
                    log.warn("Failed to extract tools[0].outputSchema.properties on doc id={}: {}", doc.getId(),
                            e.getMessage());
                }
            }
            entry.put("outputs", outputs);

            agents.put(entry);
        }

        return Blobs.createJSONBlob(agents.toString());
    }

}
