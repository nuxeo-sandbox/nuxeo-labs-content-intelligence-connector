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

import org.apache.commons.lang3.StringUtils;
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
 * Generates image metadata via {@code imageMetadataGeneration} and writes the parsed
 * {@code field/value} list into the {@code CICMetadataDetection} facet.
 *
 * @since 2025.18
 */
@Operation(id = CICGetImageMetadataOp.ID, category = "CIC", label = "CIC: Get Image Metadata", description = ""
        + "Calls Hyland Knowledge Enrichment imageMetadataGeneration and writes the parsed metadata items into"
        + " cic_metadata_detection:metadata (CICMetadataDetection facet added when needed).")
public class CICGetImageMetadataOp extends AbstractCICImageEnrichmentOp {

    public static final String ID = "CIC.GetImageMetadata";

    @Context
    protected CoreSession session;

    @Param(name = "configName", required = false)
    protected String configNameParam;

    @Param(name = "renditionName", required = false)
    protected String renditionNameParam;

    @Param(name = "instructionsV2JsonStr", required = false)
    protected String instructionsV2JsonStr;

    /**
     * <b>Required.</b> Caller-supplied {@code kSimilarMetadata} JSON array string for KE v2
     * {@code imageMetadataGeneration} (the action will not run without it). Expected format:
     * {@code [{"category:field":"Value1|Value2|...", ...}]}. The plugin does NOT provide a
     * Java-side fallback: the generic example values shipped with the Web UI button live in the
     * {@code cic-ke-image-metadata} slot-content. Real deployments should pass their own example
     * via a Studio Automation chain wrapping this operation — see the README.
     *
     * @since 2025.18
     */
    @Param(name = "kSimilarMetadataJsonStr", required = true)
    protected String kSimilarMetadataJsonStr;

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
        if (StringUtils.isNotBlank(kSimilarMetadataJsonStr)) {
            json.put("kSimilarMetadataJsonStr", kSimilarMetadataJsonStr);
        }
        return json;
    }

    /** Restores the image-op fields via {@code super} + the optional {@code kSimilarMetadataJsonStr}. */
    @Override
    public void applyAsyncParams(org.json.JSONObject params) {
        super.applyAsyncParams(params);
        if (params.has("kSimilarMetadataJsonStr")) {
            this.kSimilarMetadataJsonStr = params.optString("kSimilarMetadataJsonStr", null);
        }
    }

    @Override
    protected String getActionName() {
        return "imageMetadataGeneration";
    }

    /**
     * KE v2 {@code imageMetadataGeneration} <b>requires</b> at least one example metadata object
     * ({@code kSimilarMetadata}). The caller MUST provide a non-blank
     * {@code kSimilarMetadataJsonStr} — no Java-side fallback is provided. Throws
     * {@link org.nuxeo.ecm.core.api.NuxeoException} on blank input.
     *
     * @since 2025.18
     */
    @Override
    protected String getSimilarMetadataJsonArrayStr() {
        if (StringUtils.isBlank(kSimilarMetadataJsonStr)) {
            throw new org.nuxeo.ecm.core.api.NuxeoException(
                    "CIC.GetImageMetadata: parameter 'kSimilarMetadataJsonStr' is required."
                            + " Provide a JSON array string of example metadata objects, e.g."
                            + " [{\"image:category\":\"Photo|Screenshot|Diagram\",\"keywords:tags\":\"...\"}]."
                            + " See the README for guidance.");
        }
        return kSimilarMetadataJsonStr;
    }

    @Override
    protected String getResultKey() {
        return "imageMetadata";
    }

    @Override
    protected void applyResult(DocumentModel doc, Object actionResult) {
        CICEnrichmentHelper helper = Framework.getService(CICEnrichmentHelper.class);
        helper.writeMetadataDetection(doc, actionResult);
    }

}
