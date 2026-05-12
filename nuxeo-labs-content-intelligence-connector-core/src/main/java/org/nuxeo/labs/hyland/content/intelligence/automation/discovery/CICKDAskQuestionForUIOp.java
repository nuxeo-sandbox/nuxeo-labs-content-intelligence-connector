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
package org.nuxeo.labs.hyland.content.intelligence.automation.discovery;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.labs.hyland.content.intelligence.service.discovery.HylandKDService;
import org.nuxeo.runtime.api.Framework;

/**
 * UI-oriented operation that asks a question to Knowledge Discovery and resolves
 * the returned objectReferences to actual Nuxeo documents.
 * <p>
 * Input: optional {@link DocumentModelList} used as KD context. For each document, if
 * the {@code kdinfo:objectId} property exists and is set, it is used as the KD object id;
 * otherwise the document UUID is used.
 * <p>
 * Output: a JSON {@link Blob} with the canonical envelope:
 * <pre>
 * {
 *   "responseCode": 200,
 *   "responseMessage": "OK",
 *   "response": {
 *     "question": "...",
 *     "answer": "...",
 *     "references": [{"uid":"...","title":"..."}, ...],
 *     "noReferencesMessage": "..."
 *   }
 * }
 * </pre>
 * On error (non 2xx) the upstream envelope is returned unchanged.
 *
 * @since 2025.16
 */
@Operation(id = CICKDAskQuestionForUIOp.ID, category = "Hyland Content Intelligence", label = "CIC: KD Ask Question (for UI)", description = ""
        + "Asks a question to Knowledge Discovery and resolves objectReferences to Nuxeo documents."
        + " Input is an optional document list used as KD context (kdinfo:objectId is preferred over uuid)."
        + " agentId is optional; if not passed it is read from nuxeo.hyland.cic.discovery.default.agentId."
        + " configName is the name of the XML configuration to use (default: 'default')."
        + " Returns a JSON blob with {responseCode, responseMessage, response:{question, answer, references, noReferencesMessage}}."
        + " On error the upstream envelope is returned unchanged.")
public class CICKDAskQuestionForUIOp {

    public static final String ID = "CIC.KDAskQuestionForUI";

    @Context
    protected CoreSession session;

    @Context
    protected HylandKDService kdService;

    @Param(name = "question", required = true)
    protected String question;

    @Param(name = "agentId", required = false)
    protected String agentId;

    @Param(name = "configName", required = false)
    protected String configName;

    @OperationMethod
    public Blob run(DocumentModelList input) throws InterruptedException {

        // Build KD context object ids from input docs (kdinfo:objectId preferred over uuid)
        ArrayList<String> objKeys = new ArrayList<>();
        if (input != null && !input.isEmpty()) {
            for (DocumentModel doc : input) {
                String kdObjectId = null;
                if (doc.hasSchema("kdinfo")) {
                    Object v = doc.getPropertyValue("kdinfo:objectId");
                    if (v != null) {
                        kdObjectId = v.toString();
                    }
                }
                if (StringUtils.isNotBlank(kdObjectId)) {
                    objKeys.add(kdObjectId);
                } else {
                    objKeys.add(doc.getId());
                }
            }
        }

        ServiceCallResult result = kdService.askQuestionAndGetAnswer(configName, agentId, question, objKeys, null, null);

        int responseCode = result.getResponseCode();
        // On error, return the upstream envelope unchanged
        if (responseCode < 200 || responseCode > 299) {
            return Blobs.createJSONBlob(result.toJsonString());
        }

        JSONObject upstream = new JSONObject(result.toJsonString());
        JSONObject upstreamResponse = upstream.optJSONObject("response");
        if (upstreamResponse == null) {
            upstreamResponse = new JSONObject();
        }

        // Resolve references: extract Nuxeo doc ids from objectReferences[*].objectId
        List<String> ids = new ArrayList<>();
        JSONArray objectReferences = upstreamResponse.optJSONArray("objectReferences");
        if (objectReferences != null) {
            for (int i = 0; i < objectReferences.length(); i++) {
                JSONObject oneRef = objectReferences.optJSONObject(i);
                if (oneRef == null) {
                    continue;
                }
                String objectId = oneRef.optString("objectId", null);
                if (StringUtils.isBlank(objectId)) {
                    continue;
                }
                ids.add(extractNuxeoDocID(objectId));
            }
        }

        JSONArray references = new JSONArray();
        String noReferencesMessage = "";
        if (!ids.isEmpty()) {
            // Build NXQL: SELECT * FROM Document WHERE ecm:uuid IN (...) [OR kdinfo:objectId IN (...)]
            String idsList = "\"" + String.join("\",\"", ids) + "\"";
            String nxql = "SELECT * FROM Document WHERE ecm:uuid IN (" + idsList + ")";
            // Only add kdinfo:objectId clause if the schema is registered
            boolean kdinfoSchemaAvailable = isKdinfoSchemaAvailable();
            if (kdinfoSchemaAvailable) {
                nxql += " OR kdinfo:objectId IN (" + idsList + ")";
            }

            DocumentModelList docs = session.query(nxql);
            for (DocumentModel doc : docs) {
                JSONObject ref = new JSONObject();
                ref.put("uid", doc.getId());
                ref.put("title", doc.getTitle());
                references.put(ref);
            }

            if (docs.isEmpty()) {
                if (ids.size() == 1) {
                    noReferencesMessage = "The document used for reference in the Knowledge Discovery repository is not in this Nuxeo repository.";
                } else {
                    noReferencesMessage = "The " + ids.size()
                            + " documents used for reference in the Knowledge Discovery repository are not in this Nuxeo repository.";
                }
            }
        }

        // Build the new envelope
        JSONObject newResponse = new JSONObject();
        newResponse.put("question", upstreamResponse.opt("question"));
        newResponse.put("answer", upstreamResponse.opt("answer"));
        newResponse.put("references", references);
        newResponse.put("noReferencesMessage", noReferencesMessage);

        JSONObject finalEnvelope = new JSONObject();
        finalEnvelope.put("responseCode", responseCode);
        finalEnvelope.put("responseMessage", upstream.opt("responseMessage"));
        finalEnvelope.put("response", newResponse);

        return Blobs.createJSONBlob(finalEnvelope.toString());
    }

    /**
     * Reproduces the JS extractNuxeoDocID helper: split on "__" and return parts[1] if it
     * exists, otherwise parts[0].
     */
    protected static String extractNuxeoDocID(String objectId) {
        String[] parts = objectId.split("__");
        return parts.length > 1 ? parts[1] : parts[0];
    }

    /**
     * Checks whether the {@code kdinfo} schema is registered in this runtime. When the
     * schema is not deployed (default in this plugin), references are resolved only via
     * {@code ecm:uuid}.
     */
    protected boolean isKdinfoSchemaAvailable() {
        SchemaManager sm = Framework.getService(SchemaManager.class);
        return sm != null && sm.getSchema("kdinfo") != null;
    }

}
