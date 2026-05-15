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
 *     Thibaud Arguillere (With the help of Opencode/Claude Opus for the Web UI port from a Studio project)
 */
package org.nuxeo.labs.hyland.content.intelligence.automation.discovery;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;

/**
 * Returns the list of agents made available to the current Nuxeo application by
 * querying the local {@code CICAgentAndConfig} documents (NOT the full list of
 * agents declared on the CIC platform). This is the local Nuxeo equivalent of
 * "which agents do we expose to the end-user".
 * <p>
 * The query uses the user's session, so each user only sees agents on which they
 * have READ permission. Granting READ on the {@code CICAgentAndConfig} documents
 * is therefore the way a Nuxeo administrator controls who can use which agent.
 * <p>
 * Output is a JSON {@link Blob} holding a plain array (NOT the canonical
 * {@code {responseCode, response, ...}} envelope used by ops that wrap a CIC HTTP
 * call), each entry being:
 * 
 * <pre>
 * { "title": "...", "agentId": "..." }
 * </pre>
 *
 * Used by Web UI elements (e.g. {@code kd-conversation}) that need to populate
 * an agent picker.
 *
 * @since 2025.16
 */
@Operation(id = HylandKDAvailableAgentsOp.ID, category = "Hyland Content Intelligence", label = "Get Available KD Agents (Local)", description = ""
        + "Returns a JSON Blob holding a plain array of the locally registered CICAgentAndConfig"
        + " documents the current user has READ access to. Each entry is {title, agentId}."
        + " Intended for Web UI elements (e.g. kd-conversation) that need to populate an agent picker."
        + " This is NOT the full list of agents declared on the CIC platform; use HylandKnowledgeDiscovery.getAllAgents for that.")
public class HylandKDAvailableAgentsOp {

    public static final String ID = "HylandKD.AvailableAgents";

    protected static final String NXQL = "SELECT * FROM CICAgentAndConfig"
            + " WHERE ecm:isVersion = 0 AND ecm:isTrashed = 0 AND ecm:isProxy = 0";

    @Context
    protected CoreSession session;

    @OperationMethod
    public Blob run() {

        var docs = session.query(NXQL);
        var agents = new JSONArray();
        for (DocumentModel doc : docs) {
            var entry = new JSONObject();
            entry.put("title", doc.getTitle());
            entry.put("agentId", (String) doc.getPropertyValue("cicagentandconfig:agentId"));
            agents.put(entry);
        }
        return Blobs.createJSONBlob(agents.toString());
    }

}
