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
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.CICEnrichmentHelper;
import org.nuxeo.runtime.api.Framework;

/**
 * Extracts text metadata (company, owner, security, keywords, moreMetadata) into the
 * {@code cic_text_metadata} schema.
 *
 * @since 2025.18
 */
@Operation(id = CICGetTextMetadataOp.ID, category = "CIC", label = "CIC: Get Text Metadata", description = ""
        + "Calls Hyland Knowledge Enrichment textMetadataGeneration and writes the parsed values into"
        + " the cic_text_metadata schema (company, owner, security, keywords, moreMetadata).")
public class CICGetTextMetadataOp extends AbstractCICTextEnrichmentOp {

    public static final String ID = "CIC.GetTextMetadata";

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

    @OperationMethod
    public DocumentModel run(DocumentModel doc) {
        this.xpath = xpathParam;
        return runForDocument(session, doc, configName, instructionsV2JsonStr, saveDocument);
    }

    @Override
    protected String getActionName() {
        return "textMetadataGeneration";
    }

    @Override
    protected String getResultKey() {
        return "textMetadata";
    }

    @Override
    protected void applyResult(DocumentModel doc, Object actionResult, ServiceCallResult fullResult) {
        CICEnrichmentHelper helper = Framework.getService(CICEnrichmentHelper.class);
        helper.writeTextMetadata(doc, actionResult);
    }

}
