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

import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.CICEnrichmentHelper;
import org.nuxeo.runtime.api.Framework;

/**
 * Generates a textual description of the document's picture rendition and stores it in the
 * {@code CICImageDescription} facet.
 * <p>
 * Supports two input shapes:
 * <ul>
 * <li>{@link DocumentModel} — single doc, returns the mutated document.</li>
 * <li>{@link DocumentModelList} — multiple docs, processed in sequential batches (see
 * {@code batchSize} param). Returns the same list reference, each document mutated in place
 * (success: {@code cic_image_description:description} written + any prior {@code CICError}
 * cleared; failure: {@code CICError} facet populated). When {@code saveDocument=false} the caller
 * is responsible for persisting the returned list — strongly recommend setting
 * {@code saveDocument=true} for multi-doc calls.</li>
 * </ul>
 *
 * @since 2025.18
 */
@Operation(id = CICGetImageDescriptionOp.ID, category = "CIC", label = "CIC: Get Image Description", description = ""
        + "Calls Hyland Knowledge Enrichment imageDescription on the document's picture rendition (default rendition"
        + " is descriptor-configured, falling back to FullHD) and writes the description into"
        + " cic_image_description:description (CICImageDescription facet added when needed).\n\n"
        + "Accepts either a single DocumentModel (returns the modified document) or a DocumentModelList"
        + " (returns the same list, each document mutated in place; processed in sequential batches of"
        + " batchSize — defaults to nuxeo.hyland.cic.enrichment.batchSize / 10).\n\n"
        + "When saveDocument=false (default), callers are responsible for saving the returned document(s);"
        + " strongly recommended to pass saveDocument=true for multi-document calls.")
public class CICGetImageDescriptionOp extends AbstractCICImageEnrichmentOp {

    public static final String ID = "CIC.GetImageDescription";

    @Context
    protected CoreSession session;

    @Param(name = "configName", required = false)
    protected String configNameParam;

    @Param(name = "renditionName", required = false)
    protected String renditionNameParam;

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
     * When {@code true}, the call is scheduled as a background {@link CICEnrichmentWork} and the
     * operation returns the input document(s) immediately (unchanged). The {@code saveDocument}
     * value is forced to {@code true} inside the Work — async callers cannot inspect the result.
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

    /** Captures the {@code @Param} values into the params JSON consumed by {@link CICEnrichmentWork}. */
    protected org.json.JSONObject buildParamsJson() {
        org.json.JSONObject json = baseParamsJson(configNameParam, instructionsV2JsonStr, saveDocument, batchSize);
        if (renditionNameParam != null) {
            json.put("renditionName", renditionNameParam);
        }
        return json;
    }

    @Override
    protected String getActionName() {
        return "imageDescription";
    }

    @Override
    protected String getResultKey() {
        return "imageDescription";
    }

    @Override
    protected void applyResult(DocumentModel doc, Object actionResult) {
        CICEnrichmentHelper helper = Framework.getService(CICEnrichmentHelper.class);
        helper.writeImageDescription(doc, actionResult == null ? null : String.valueOf(actionResult));
    }

}
