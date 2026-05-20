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
 *     Thibaud Arguillere (With the help of Opencode/Claude Opus for the Web UI port from a Studio project)
 */
package org.nuxeo.labs.hyland.content.intelligence.automation.enrichment;

import java.util.List;

import org.json.JSONObject;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.util.StringList;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.CICEnrichmentHelper;
import org.nuxeo.runtime.api.Framework;

/**
 * Classifies a picture rendition and writes the value into {@code cic_classification:imageClass}.
 * <p>
 * If the {@code classes} param is null/empty, all entry ids of the {@code cicImageClassification}
 * vocabulary are used as the candidate list.
 *
 * @since 2025.18
 */
@Operation(id = CICClassifyImageOp.ID, category = "CIC", label = "CIC: Classify Image", description = ""
        + "Calls Hyland Knowledge Enrichment imageClassification, falling back to the cicImageClassification"
        + " vocabulary entries when no classes are passed. Writes the chosen class into"
        + " cic_classification:imageClass (CICClassification facet added when needed).")
public class CICClassifyImageOp extends AbstractCICImageEnrichmentOp {

    public static final String ID = "CIC.ClassifyImage";

    public static final String DEFAULT_DIRECTORY = "cicImageClassification";

    @Context
    protected CoreSession session;

    @Param(name = "configName", required = false)
    protected String configNameParam;

    @Param(name = "renditionName", required = false)
    protected String renditionNameParam;

    @Param(name = "classes", required = false)
    protected StringList classes;

    @Param(name = "instructionsV2JsonStr", required = false)
    protected String instructionsV2JsonStr;

    @Param(name = "saveDocument", required = false, values = "false")
    protected boolean saveDocument = false;

    /**
     * Batch size used when the operation is invoked with a {@link DocumentModelList} input.
     * Values {@code <= 0} fall back to the configured default
     * ({@code nuxeo.hyland.cic.enrichment.batchSize} / 10).
     *
     * @since 2025.16
     */
    @Param(name = "batchSize", required = false, values = "0")
    protected int batchSize = 0;

    /**
     * When {@code true}, schedule as a background {@link CICEnrichmentWork} and return the input
     * unchanged. {@code saveDocument} is forced to {@code true} inside the Work.
     *
     * @since 2025.16
     */
    @Param(name = "runAsynchronously", required = false, values = "false")
    protected boolean runAsynchronously = false;

    @OperationMethod
    public DocumentModel run(DocumentModel doc) {
        this.configName = configNameParam;
        this.renditionName = renditionNameParam;
        if (runAsynchronously) {
            scheduleAsyncForDocument(session, doc, buildParamsJson());
            return doc;
        }
        return runForDocument(session, doc, configNameParam, instructionsV2JsonStr, saveDocument);
    }

    @OperationMethod
    public DocumentModelList run(DocumentModelList docs) {
        this.configName = configNameParam;
        this.renditionName = renditionNameParam;
        if (runAsynchronously) {
            scheduleAsyncForDocuments(session, docs, buildParamsJson());
            return docs;
        }
        return runForDocuments(session, docs, configNameParam, instructionsV2JsonStr, saveDocument, batchSize);
    }

    protected org.json.JSONObject buildParamsJson() {
        org.json.JSONObject json = baseParamsJson(configNameParam, instructionsV2JsonStr, saveDocument, batchSize);
        if (renditionNameParam != null) {
            json.put("renditionName", renditionNameParam);
        }
        if (classes != null && !classes.isEmpty()) {
            json.put("classes", new org.json.JSONArray(classes));
        }
        return json;
    }

    /** Restores image fields via {@code super} + the {@code classes} list. */
    @Override
    public void applyAsyncParams(org.json.JSONObject params) {
        super.applyAsyncParams(params);
        org.json.JSONArray arr = params.optJSONArray("classes");
        if (arr != null) {
            org.nuxeo.ecm.automation.core.util.StringList list = new org.nuxeo.ecm.automation.core.util.StringList();
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.optString(i, null));
            }
            this.classes = list;
        }
    }

    @Override
    protected List<String> getClasses() {
        if (classes != null && !classes.isEmpty()) {
            return classes;
        }
        return loadDirectoryIds(DEFAULT_DIRECTORY);
    }

    @Override
    protected String getActionName() {
        return "imageClassification";
    }

    @Override
    protected String getResultKey() {
        return "imageClassification";
    }

    @Override
    protected void applyResult(DocumentModel doc, Object actionResult) {
        CICEnrichmentHelper helper = Framework.getService(CICEnrichmentHelper.class);
        String value = extractClassValue(actionResult);
        helper.writeClassification(doc, "imageClass", value);
    }

    /** Tolerates either a scalar or {@code {classification|value|class|label: "<value>"}}. */
    protected String extractClassValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof JSONObject jo) {
            for (String k : new String[] { "classification", "value", "class", "label" }) {
                if (jo.has(k)) {
                    return jo.optString(k, null);
                }
            }
        }
        return String.valueOf(raw);
    }

}
