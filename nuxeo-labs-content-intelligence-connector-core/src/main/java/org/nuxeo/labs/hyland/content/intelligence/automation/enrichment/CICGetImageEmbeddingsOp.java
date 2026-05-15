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
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.CICEnrichmentHelper;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.HylandKEService;
import org.nuxeo.runtime.api.Framework;

/**
 * Computes image embeddings for the document's picture rendition. If the KE descriptor for the
 * resolved {@code configName} does not declare both {@code embeddingsFacet} and
 * {@code embeddingsImageXpath}, the call is skipped (a WARN is logged) and the document is
 * returned unchanged \u2014 no remote CIC call is made.
 *
 * @since 2025.18
 */
@Operation(id = CICGetImageEmbeddingsOp.ID, category = "CIC", label = "CIC: Get Image Embeddings", description = ""
        + "Calls Hyland Knowledge Enrichment imageEmbeddings on the document's picture rendition. Writes embeddings"
        + " to the descriptor-configured embeddings facet/xpath. If the descriptor does not configure"
        + " embeddingsFacet + embeddingsImageXpath, the call is skipped (a WARN is logged) and the document is"
        + " returned unchanged.")
public class CICGetImageEmbeddingsOp extends AbstractCICImageEnrichmentOp {

    public static final String ID = "CIC.GetImageEmbeddings";

    private static final Logger LOG = LogManager.getLogger(CICGetImageEmbeddingsOp.class);

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

    @OperationMethod
    public DocumentModel run(DocumentModel doc) {
        HylandKEService ke = Framework.getService(HylandKEService.class);
        String facet = ke.getEmbeddingsFacet(configNameParam);
        String xpath = ke.getEmbeddingsImageXpath(configNameParam);
        if (StringUtils.isBlank(facet) || StringUtils.isBlank(xpath)) {
            LOG.warn(
                    "CIC.GetImageEmbeddings skipped: KE descriptor '{}' has no embeddingsFacet/embeddingsImageXpath configured. Document {} returned unchanged (no CIC call).",
                    configNameParam == null ? "default" : configNameParam, doc.getId());
            return doc;
        }
        this.configName = configNameParam;
        this.renditionName = renditionNameParam;
        return runForDocument(session, doc, configNameParam, instructionsV2JsonStr, saveDocument);
    }

    @Override
    protected String getActionName() {
        return "imageEmbeddings";
    }

    @Override
    protected String getResultKey() {
        return "imageEmbeddings";
    }

    @Override
    protected void applyResult(DocumentModel doc, Object actionResult) {
        CICEnrichmentHelper helper = Framework.getService(CICEnrichmentHelper.class);
        helper.writeImageEmbeddings(doc, configName, actionResult);
    }

}
