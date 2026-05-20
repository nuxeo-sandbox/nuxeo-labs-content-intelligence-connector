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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.CICEnrichmentHelper;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.HylandKEService;
import org.nuxeo.runtime.api.Framework;

/**
 * Computes text embeddings for the document's text blob. If the KE descriptor for the resolved
 * {@code configName} does not declare both {@code embeddingsFacet} and {@code embeddingsTextXpath},
 * the call is skipped (a WARN is logged) and the document is returned unchanged \u2014 no remote
 * CIC call is made.
 *
 * @since 2025.18
 */
@Operation(id = CICGetTextEmbeddingsOp.ID, category = "CIC", label = "CIC: Get Text Embeddings", description = ""
        + "Calls Hyland Knowledge Enrichment textEmbeddings. Writes embeddings to the descriptor-configured"
        + " embeddings facet/xpath. If the descriptor does not configure embeddingsFacet + embeddingsTextXpath,"
        + " the call is skipped (a WARN is logged) and the document is returned unchanged.")
public class CICGetTextEmbeddingsOp extends AbstractCICTextEnrichmentOp {

    public static final String ID = "CIC.GetTextEmbeddings";

    private static final Logger LOG = LogManager.getLogger(CICGetTextEmbeddingsOp.class);

    @Context
    protected CoreSession session;

    @Param(name = "configName", required = false)
    protected String configName;

    @Param(name = "xpath", required = false)
    protected String xpathParam;

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
     * unchanged. {@code saveDocument} is forced to {@code true} inside the Work. The
     * descriptor-level embeddings configuration is checked synchronously before scheduling — if
     * missing, no Work is scheduled.
     *
     * @since 2025.16
     */
    @Param(name = "runAsynchronously", required = false, values = "false")
    protected boolean runAsynchronously = false;

    protected String currentConfigName;

    @OperationMethod
    public DocumentModel run(DocumentModel doc) {
        if (isEmbeddingsNotConfigured(doc.getId())) {
            return doc;
        }
        this.xpath = xpathParam;
        this.currentConfigName = configName;
        if (runAsynchronously) {
            scheduleAsyncForDocument(session, doc, buildParamsJson());
            return doc;
        }
        return runForDocument(session, doc, configName, instructionsV2JsonStr, saveDocument);
    }

    @OperationMethod
    public DocumentModelList run(DocumentModelList docs) {
        if (docs == null || docs.isEmpty()) {
            return docs;
        }
        if (isEmbeddingsNotConfigured("<list of " + docs.size() + " docs>")) {
            return docs;
        }
        this.xpath = xpathParam;
        this.currentConfigName = configName;
        if (runAsynchronously) {
            scheduleAsyncForDocuments(session, docs, buildParamsJson());
            return docs;
        }
        return runForDocuments(session, docs, configName, instructionsV2JsonStr, saveDocument, batchSize);
    }

    protected org.json.JSONObject buildParamsJson() {
        org.json.JSONObject json = baseParamsJson(configName, instructionsV2JsonStr, saveDocument, batchSize);
        if (xpathParam != null) {
            json.put("xpath", xpathParam);
        }
        return json;
    }

    /** Restores text-op fields via {@code super} + {@code currentConfigName} (needed by applyResult). */
    @Override
    public void applyAsyncParams(org.json.JSONObject params) {
        super.applyAsyncParams(params);
        this.currentConfigName = params.has("configName") ? params.optString("configName", null) : null;
    }

    /** Returns true and WARN-logs when the descriptor does not configure text embeddings. */
    protected boolean isEmbeddingsNotConfigured(String contextLabel) {
        HylandKEService ke = Framework.getService(HylandKEService.class);
        String facet = ke.getEmbeddingsFacet(configName);
        String xpath = ke.getEmbeddingsTextXpath(configName);
        if (StringUtils.isBlank(facet) || StringUtils.isBlank(xpath)) {
            LOG.warn(
                    "CIC.GetTextEmbeddings skipped: KE descriptor '{}' has no embeddingsFacet/embeddingsTextXpath configured. {} returned unchanged (no CIC call).",
                    configName == null ? "default" : configName, contextLabel);
            return true;
        }
        return false;
    }

    @Override
    protected String getActionName() {
        return "textEmbeddings";
    }

    @Override
    protected String getResultKey() {
        return "textEmbeddings";
    }

    @Override
    protected void applyResult(DocumentModel doc, Object actionResult) {
        CICEnrichmentHelper helper = Framework.getService(CICEnrichmentHelper.class);
        helper.writeTextEmbeddings(doc, currentConfigName, actionResult);
    }

}
