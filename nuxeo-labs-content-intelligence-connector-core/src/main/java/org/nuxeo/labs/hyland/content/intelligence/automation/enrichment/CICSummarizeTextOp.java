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
 *     Thibaud Arguillere
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
 * Generates a textual summary for the document's text blob and stores it in the {@code CICSummary}
 * facet ({@code cic_summary:summary}).
 *
 * @since 2025.18
 */
@Operation(id = CICSummarizeTextOp.ID, category = "CIC", label = "CIC: Summarize Text", description = ""
        + "Calls Hyland Knowledge Enrichment textSummarization on the document's text blob (default xpath: file:content)"
        + " and writes the result in the cic_summary:summary field (CICSummary facet is added when needed)."
        + " On error, populates the cic_error schema (CICError facet)."
        + " saveDocument controls whether the document is saved.")
public class CICSummarizeTextOp extends AbstractCICTextEnrichmentOp {

    public static final String ID = "CIC.SummarizeText";

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
        return "textSummarization";
    }

    @Override
    protected String getResultKey() {
        return "textSummary";
    }

    @Override
    protected void applyResult(DocumentModel doc, Object actionResult, ServiceCallResult fullResult) {
        CICEnrichmentHelper helper = Framework.getService(CICEnrichmentHelper.class);
        helper.writeSummary(doc, actionResult == null ? null : String.valueOf(actionResult));
    }

}
