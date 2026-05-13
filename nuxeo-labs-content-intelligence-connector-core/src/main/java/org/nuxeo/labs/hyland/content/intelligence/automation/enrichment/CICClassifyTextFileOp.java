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
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.CICEnrichmentHelper;
import org.nuxeo.runtime.api.Framework;

/**
 * Classifies a text blob and writes the value into {@code cic_classification:textClass}.
 * <p>
 * If the {@code classes} param is null/empty, all entry ids of the {@code cicTextClassification}
 * vocabulary are used as the candidate list.
 *
 * @since 2025.18
 */
@Operation(id = CICClassifyTextFileOp.ID, category = "CIC", label = "CIC: Classify Text File", description = ""
        + "Calls Hyland Knowledge Enrichment textClassification, falling back to the cicTextClassification"
        + " vocabulary entries when no classes are passed. Writes the chosen class into"
        + " cic_classification:textClass (CICClassification facet added when needed).")
public class CICClassifyTextFileOp extends AbstractCICTextEnrichmentOp {

    public static final String ID = "CIC.ClassifyTextFile";

    public static final String DEFAULT_DIRECTORY = "cicTextClassification";

    @Context
    protected CoreSession session;

    @Param(name = "configName", required = false)
    protected String configName;

    @Param(name = "xpath", required = false)
    protected String xpathParam;

    @Param(name = "classes", required = false)
    protected StringList classes;

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
    protected List<String> getClasses() {
        if (classes != null && !classes.isEmpty()) {
            return classes;
        }
        return loadDirectoryIds(DEFAULT_DIRECTORY);
    }

    @Override
    protected String getActionName() {
        return "textClassification";
    }

    @Override
    protected String getResultKey() {
        return "textClassification";
    }

    @Override
    protected void applyResult(DocumentModel doc, Object actionResult, ServiceCallResult fullResult) {
        CICEnrichmentHelper helper = Framework.getService(CICEnrichmentHelper.class);
        String value = extractClassValue(actionResult);
        helper.writeClassification(doc, "textClass", value);
    }

    /** Tolerates either a scalar or {@code {classification: "<value>"}} / {@code {value: "<value>"}}. */
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
