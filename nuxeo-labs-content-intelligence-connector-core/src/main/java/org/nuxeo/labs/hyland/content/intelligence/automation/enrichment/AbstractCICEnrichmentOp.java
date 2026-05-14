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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.labs.hyland.content.intelligence.service.ServicesUtils;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.CICEnrichmentHelper;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.HylandKEService;
import org.nuxeo.runtime.api.Framework;

/**
 * Common scaffolding for the {@code CIC.*} document operations.
 * <p>
 * Subclasses implement the action-specific bits (action name, blob acquisition, result writer)
 * via {@link #getActionName()}, {@link #getBlob(DocumentModel)}, and
 * {@link #applyResult(DocumentModel, Object)}. This base class handles:
 * <ul>
 * <li>Calling {@link HylandKEService#enrich} with the proper {@code extraJsonPayloadStr} (built from
 * {@code instructionsV2JsonStr} via {@link ServicesUtils#addInstructionsToExtraPayload}).</li>
 * <li>Recognising failures (non-200 response code, or {@code response.status != "SUCCESS"}, or an
 * action-level {@code error}) and recording them via
 * {@link HylandKEService#setCICError(DocumentModel, String, int, String, String, String)}. On
 * upstream failures the full {@link ServiceCallResult} envelope JSON (HTTP code, message, response
 * body, optional object key mapping) is stored in {@code cic_error:fullResponseJson} via
 * {@link ServiceCallResult#toJsonString()}.</li>
 * <li>Clearing any previous {@code CICError} when the call succeeds.</li>
 * <li>Optionally saving the document.</li>
 * </ul>
 *
 * @since 2025.18
 */
public abstract class AbstractCICEnrichmentOp {

    private static final Logger LOG = LogManager.getLogger(AbstractCICEnrichmentOp.class);

    /** Returns the v2 action name (e.g. {@code "textSummarization"}). */
    protected abstract String getActionName();

    /**
     * Returns the result key inside {@code response.results[0]} for this action
     * (e.g. {@code "textSummary"} for {@code textSummarization}).
     */
    protected abstract String getResultKey();

    /** Returns the blob to send to KE, or {@code null} to abort. */
    protected abstract Blob getBlob(DocumentModel doc);

    /**
     * Called when the action result is available. Implementations should write it on {@code doc}
     * (no save).
     *
     * @param doc           the input document
     * @param actionResult  the {@code response.results[0].<resultKey>.result} value (already
     *                      extracted by the base class)
     */
    protected abstract void applyResult(DocumentModel doc, Object actionResult);

    /**
     * Returns the optional {@code classes} list for classification. Default is {@code null}.
     * Override in classification ops.
     */
    protected List<String> getClasses() {
        return null;
    }

    /**
     * Loads all entry IDs from a Nuxeo SQL directory. Used by classification ops to fall back to
     * the {@code cicTextClassification} / {@code cicImageClassification} vocabularies when no
     * explicit {@code classes} list was provided. All entries (including those marked obsolete)
     * are returned.
     *
     * @param directoryName the directory name (e.g. {@code "cicTextClassification"})
     * @return the list of {@code id} values, or an empty list when the directory is empty
     */
    protected List<String> loadDirectoryIds(String directoryName) {
        DirectoryService ds = Framework.getService(DirectoryService.class);
        List<String> out = new ArrayList<>();
        try (Session ds2 = ds.open(directoryName)) {
            for (DocumentModel entry : ds2.query(java.util.Map.of(), java.util.Set.of())) {
                String id = (String) entry.getPropertyValue(ds2.getIdField());
                if (StringUtils.isNotBlank(id)) {
                    out.add(id);
                }
            }
        }
        return out;
    }

    /**
     * Runs the operation. Caller controls saving via {@code saveDocument}.
     *
     * @param session              core session (for save)
     * @param doc                  the input document
     * @param configName           KE contribution name; falls back to {@code "default"} when blank
     * @param instructionsV2JsonStr optional v2 instructions JSON object string for this action
     * @param saveDocument         when {@code true}, save the document after writing
     * @return the (modified) document
     */
    public DocumentModel runForDocument(CoreSession session, DocumentModel doc, String configName,
            String instructionsV2JsonStr, boolean saveDocument) {

        if (doc == null) {
            throw new NuxeoException("Input document is required");
        }
        HylandKEService ke = Framework.getService(HylandKEService.class);
        CICEnrichmentHelper helper = Framework.getService(CICEnrichmentHelper.class);

        Blob blob = getBlob(doc);
        if (blob == null) {
            ke.setCICError(doc, HylandKEService.SERVICE_LABEL, 0, "No blob",
                    "No blob available for action " + getActionName() + " on " + doc.getId(), null);
            if (saveDocument) {
                session.saveDocument(doc);
            }
            return doc;
        }

        String extra = ServicesUtils.addInstructionsToExtraPayload(instructionsV2JsonStr, null);

        ServiceCallResult result;
        try {
            result = ke.enrich(StringUtils.isBlank(configName) ? null : configName, blob,
                    List.of(getActionName()), getClasses(), null, extra);
        } catch (IOException e) {
            ke.setCICError(doc, HylandKEService.SERVICE_LABEL, 0, "IO error calling KE",
                    e.getMessage(), null);
            if (saveDocument) {
                session.saveDocument(doc);
            }
            return doc;
        }

        // Top-level response code
        if (result.getResponseCode() != 200) {
            ke.setCICError(doc, HylandKEService.SERVICE_LABEL, result.getResponseCode(),
                    "KE call failed", result.getResponseMessage(), result.toJsonString());
            if (saveDocument) {
                session.saveDocument(doc);
            }
            return doc;
        }

        // Inspect inner envelope
        JSONObject envelope = helper.parseEnrichmentResponse(result.toJsonString());
        if (envelope == null) {
            ke.setCICError(doc, HylandKEService.SERVICE_LABEL, 200, "Invalid envelope",
                    "Could not parse KE envelope", result.toJsonString());
            if (saveDocument) {
                session.saveDocument(doc);
            }
            return doc;
        }

        JSONObject response = envelope.optJSONObject("response");
        String status = response == null ? null : response.optString("status", null);
        if (response == null || !"SUCCESS".equals(status)) {
            ke.setCICError(doc, HylandKEService.SERVICE_LABEL, 200,
                    "KE response not SUCCESS",
                    "status=" + status, result.toJsonString());
            if (saveDocument) {
                session.saveDocument(doc);
            }
            return doc;
        }

        // results[0].<resultKey>
        var results = response.optJSONArray("results");
        JSONObject resultEntry = results == null || results.isEmpty() ? null : results.optJSONObject(0);
        JSONObject actionWrapper = resultEntry == null ? null : resultEntry.optJSONObject(getResultKey());
        if (actionWrapper == null) {
            ke.setCICError(doc, HylandKEService.SERVICE_LABEL, 200,
                    "Missing action result", "Result key not found: " + getResultKey(), result.toJsonString());
            if (saveDocument) {
                session.saveDocument(doc);
            }
            return doc;
        }
        // Action-level error?
        Object actionError = actionWrapper.opt("error");
        if (actionError != null && actionError != JSONObject.NULL && !String.valueOf(actionError).isEmpty()) {
            ke.setCICError(doc, HylandKEService.SERVICE_LABEL, 200, "Action error",
                    String.valueOf(actionError), result.toJsonString());
            if (saveDocument) {
                session.saveDocument(doc);
            }
            return doc;
        }

        Object actionResult = actionWrapper.opt("result");
        if (actionResult == null || actionResult == JSONObject.NULL) {
            ke.setCICError(doc, HylandKEService.SERVICE_LABEL, 200, "Empty action result",
                    "Action returned no result", result.toJsonString());
            if (saveDocument) {
                session.saveDocument(doc);
            }
            return doc;
        }

        try {
            applyResult(doc, actionResult);
            ke.clearCICError(doc);
        } catch (RuntimeException ex) {
            LOG.warn("applyResult failed for action {}: {}", getActionName(), ex.getMessage(), ex);
            ke.setCICError(doc, HylandKEService.SERVICE_LABEL, 200, "Failed writing result",
                    ex.getMessage(), result.toJsonString());
        }

        if (saveDocument) {
            session.saveDocument(doc);
        }
        return doc;
    }

}
