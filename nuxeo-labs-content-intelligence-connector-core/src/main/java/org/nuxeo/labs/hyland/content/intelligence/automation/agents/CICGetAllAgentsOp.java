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

import org.json.JSONArray;
import org.json.JSONObject;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.labs.hyland.content.intelligence.service.agents.HylandAgentsService;
import org.nuxeo.labs.hyland.content.intelligence.service.discovery.HylandKDService;
import org.nuxeo.runtime.api.Framework;

/**
 * Returns a simplified list of agents for a given type ("KD" or "Agentic", case insensitive) and configuration. The
 * returned JSON blob has the canonical envelope {@code {responseCode, responseMessage, response: [{id,name,description},...]}}.
 * On any non-200 upstream response, the raw upstream blob is returned untouched.
 *
 * @since 2025.16
 */
@Operation(id = CICGetAllAgentsOp.ID, category = "CIC", label = "CIC: Get All Agents", description = ""
        + "Returns a simplified JSON blob {responseCode, responseMessage, response:[{id,name,description},...]}."
        + " typeOfAgent must be 'KD' or 'Agentic' (case insensitive)."
        + " configName is the name of the XML configuration to use (default 'default').")
public class CICGetAllAgentsOp {

    public static final String ID = "CIC.GetAllAgents";

    @Param(name = "typeOfAgent", required = true)
    protected String typeOfAgent;

    @Param(name = "configName", required = false)
    protected String configName;

    @OperationMethod
    public Blob run() throws OperationException {

        String type = typeOfAgent == null ? "" : typeOfAgent.trim();
        ServiceCallResult result;

        if ("KD".equalsIgnoreCase(type)) {
            HylandKDService kd = Framework.getService(HylandKDService.class);
            result = kd.getAllAgents(configName, Collections.emptyMap());
        } else if ("Agentic".equalsIgnoreCase(type)) {
            HylandAgentsService agents = Framework.getService(HylandAgentsService.class);
            result = agents.getAllAgents(configName, Collections.emptyMap());
        } else {
            throw new OperationException("typeOfAgent must be 'KD' or 'Agentic'.");
        }

        String rawJson = result.toJsonString();

        // On non-200 returns the raw envelope unchanged.
        if (result.getResponseCode() != 200) {
            return Blobs.createJSONBlob(rawJson);
        }

        var envelope = new JSONObject(rawJson);
        Object responseObj = envelope.opt("response");

        // KD => response is an array of agents
        // Agentic => response is an object with an "agents" array
        JSONArray sourceArray = null;
        if ("KD".equalsIgnoreCase(type) && responseObj instanceof JSONArray arr) {
            sourceArray = arr;
        } else if ("Agentic".equalsIgnoreCase(type) && responseObj instanceof JSONObject obj
                && obj.has("agents") && obj.get("agents") instanceof JSONArray arr) {
            sourceArray = arr;
        }

        var simplified = new JSONArray();
        if (sourceArray != null) {
            for (int i = 0; i < sourceArray.length(); i++) {
                Object item = sourceArray.get(i);
                if (item instanceof JSONObject agent) {
                    var simple = new JSONObject();
                    simple.put("id", agent.opt("id"));
                    simple.put("name", agent.opt("name"));
                    simple.put("description", agent.opt("description"));
                    simplified.put(simple);
                }
            }
        }

        var finalJson = new JSONObject();
        finalJson.put("responseCode", result.getResponseCode());
        finalJson.put("responseMessage", result.getResponseMessage());
        finalJson.put("response", simplified);

        return Blobs.createJSONBlob(finalJson.toString());
    }

}
