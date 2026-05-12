/*
 * (C) Copyright 2025 Hyland (http://hyland.com/)  and others.
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

import java.util.Collections;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.labs.hyland.content.intelligence.service.agents.HylandAgentsService;
import org.nuxeo.runtime.api.Framework;

/**
 * Looks up an Agentic agent on a {@code CICAgenticAgentAndConfig} document, then writes its input schema and tools as
 * JSON strings into {@code cicagenticagentandconfig:inputSchemaJson} and
 * {@code cicagenticagentandconfig:toolsJson}.
 *
 * @since 2025.16
 */
@Operation(id = CICAgenticAgentLookupOp.ID, category = "CIC", label = "CIC: Agentic Agent Lookup", description = ""
        + "Input is a CICAgenticAgentAndConfig document. Calls HylandAgents.LookupAgent using the document's"
        + " cicagenticagentandconfig:agentId and cicagentandconfig:configName, then saves the resulting input schema"
        + " and tools (as JSON strings) on the document. If saveDocument is true (default false), the document is saved.")
public class CICAgenticAgentLookupOp {

    public static final String ID = "CIC.AgenticAgentLookup";

    private static final Logger log = LogManager.getLogger(CICAgenticAgentLookupOp.class);

    public static final String DOCTYPE = "CICAgenticAgentAndConfig";

    public static final String XPATH_AGENT_ID = "cicagenticagentandconfig:agentId";

    public static final String XPATH_CONFIG_NAME = "cicagenticagentandconfig:configName";

    public static final String XPATH_INPUT_SCHEMA_JSON = "cicagenticagentandconfig:inputSchemaJson";

    public static final String XPATH_TOOLS_JSON = "cicagenticagentandconfig:toolsJson";

    @Context
    protected CoreSession session;

    @Param(name = "saveDocument", required = false, values = { "false" })
    protected boolean saveDocument = false;

    @OperationMethod
    public DocumentModel run(DocumentModel input) {

        if (input == null) {
            return input;
        }

        if (!DOCTYPE.equals(input.getType())) {
            log.warn("CIC.AgenticAgentLookup: input is not a {} (got {}) => doing nothing", DOCTYPE, input.getType());
            return input;
        }

        String agentId = (String) input.getPropertyValue(XPATH_AGENT_ID);
        if (agentId == null || agentId.isBlank()) {
            log.warn("CIC.AgenticAgentLookup: no agentId => doing nothing");
            return input;
        }

        String configName = (String) input.getPropertyValue(XPATH_CONFIG_NAME);

        HylandAgentsService agents = Framework.getService(HylandAgentsService.class);
        ServiceCallResult result = agents.lookupAgent(configName, agentId, null, Collections.emptyMap());

        if (result.getResponseCode() != 200) {
            log.warn("CIC.AgenticAgentLookup: response code {} ({}) => doing nothing", result.getResponseCode(),
                    result.getResponseMessage());
            return input;
        }

        // Expected envelope path: response.version.config.{inputSchema.properties, tools}
        var envelope = new JSONObject(result.toJsonString());
        JSONObject response = envelope.optJSONObject("response");
        JSONObject version = response == null ? null : response.optJSONObject("version");
        JSONObject config = version == null ? null : version.optJSONObject("config");
        if (config == null) {
            log.warn("CIC.AgenticAgentLookup: response.version.config not found => doing nothing");
            return input;
        }

        JSONObject inputSchema = config.optJSONObject("inputSchema");
        Object props = inputSchema == null ? null : inputSchema.opt("properties");
        Object tools = config.opt("tools");

        if (props != null) {
            input.setPropertyValue(XPATH_INPUT_SCHEMA_JSON, props.toString());
        }
        if (tools != null) {
            input.setPropertyValue(XPATH_TOOLS_JSON, tools.toString());
        }

        if (saveDocument) {
            input = session.saveDocument(input);
        }

        return input;
    }

}
