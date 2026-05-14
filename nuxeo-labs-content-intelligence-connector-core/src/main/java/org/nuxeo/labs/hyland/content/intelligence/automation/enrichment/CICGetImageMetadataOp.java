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

    @Param(name = "saveDocument", required = false, values = "false")
    protected boolean saveDocument = false;

    @OperationMethod
    public DocumentModel run(DocumentModel doc) {
        this.configName = configNameParam;
        this.renditionName = renditionNameParam;
        return runForDocument(session, doc, configNameParam, instructionsV2JsonStr, saveDocument);
    }

    @Override
    protected String getActionName() {
        return "imageMetadataGeneration";
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
