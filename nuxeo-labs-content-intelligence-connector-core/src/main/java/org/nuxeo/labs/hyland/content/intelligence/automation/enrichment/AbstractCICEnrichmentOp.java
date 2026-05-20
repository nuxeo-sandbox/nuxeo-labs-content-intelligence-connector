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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.labs.hyland.content.intelligence.ContentToProcess;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.labs.hyland.content.intelligence.service.ServicesUtils;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.CICEnrichmentHelper;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.HylandKEService;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

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
     * Returns the optional {@code kSimilarMetadata} JSON array string (KE v2
     * {@code textMetadataGeneration} / {@code imageMetadataGeneration} require at least one
     * example metadata object). Default is {@code null}. Override in the metadata generation ops
     * to provide a sensible generic default. Real-world usage typically requires a use-case
     * specific override (custom Automation chain or slot-content override) — see the README.
     *
     * @return a JSON array string (e.g. {@code [{"document:category":"Legal|Financial|..."}]}),
     *         or {@code null} when not applicable.
     * @since 2025.18
     */
    protected String getSimilarMetadataJsonArrayStr() {
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
                doc = session.saveDocument(doc);
            }
            return doc;
        }

        String extra = ServicesUtils.addInstructionsToExtraPayload(instructionsV2JsonStr, null);

        ServiceCallResult result;
        try {
            result = ke.enrich(StringUtils.isBlank(configName) ? null : configName, blob,
                    List.of(getActionName()), getClasses(), getSimilarMetadataJsonArrayStr(), extra);
        } catch (IOException e) {
            ke.setCICError(doc, HylandKEService.SERVICE_LABEL, 0, "IO error calling KE",
                    e.getMessage(), null);
            if (saveDocument) {
                doc = session.saveDocument(doc);
            }
            return doc;
        }

        // Top-level response code
        if (result.getResponseCode() != 200) {
            ke.setCICError(doc, HylandKEService.SERVICE_LABEL, result.getResponseCode(),
                    "KE call failed", result.getResponseMessage(), result.toJsonString());
            if (saveDocument) {
                doc = session.saveDocument(doc);
            }
            return doc;
        }

        // Inspect inner envelope
        JSONObject envelope = helper.parseEnrichmentResponse(result.toJsonString());
        if (envelope == null) {
            ke.setCICError(doc, HylandKEService.SERVICE_LABEL, 200, "Invalid envelope",
                    "Could not parse KE envelope", result.toJsonString());
            if (saveDocument) {
                doc = session.saveDocument(doc);
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
                doc = session.saveDocument(doc);
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
                doc = session.saveDocument(doc);
            }
            return doc;
        }
        // Action-level error?
        Object actionError = actionWrapper.opt("error");
        if (actionError != null && actionError != JSONObject.NULL && !String.valueOf(actionError).isEmpty()) {
            ke.setCICError(doc, HylandKEService.SERVICE_LABEL, 200, "Action error",
                    String.valueOf(actionError), result.toJsonString());
            if (saveDocument) {
                doc = session.saveDocument(doc);
            }
            return doc;
        }

        Object actionResult = actionWrapper.opt("result");
        if (actionResult == null || actionResult == JSONObject.NULL) {
            ke.setCICError(doc, HylandKEService.SERVICE_LABEL, 200, "Empty action result",
                    "Action returned no result", result.toJsonString());
            if (saveDocument) {
                doc = session.saveDocument(doc);
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
            doc = session.saveDocument(doc);
        }
        return doc;
    }

    /**
     * Multi-document variant of {@link #runForDocument}. Processes {@code docs} in sequential
     * batches and returns the same {@link DocumentModelList} reference with each document mutated
     * in place (success path: {@code applyResult} + {@code clearCICError}; failure path:
     * {@code setCICError}).
     * <p>
     * Best-effort semantics:
     * <ul>
     * <li>Docs without a blob get a {@code CICError("No blob")} and are skipped from the CIC
     * payload.</li>
     * <li>If the batch-level call to KE fails (IOException, non-2xx, status != SUCCESS, missing
     * envelope) every doc in that batch gets a {@code CICError}.</li>
     * <li>Per-result errors or missing result keys produce a {@code CICError} on the matching
     * doc.</li>
     * <li>Result entries whose {@code sourceId} is unknown in the current batch are logged as
     * WARN.</li>
     * <li>Docs from the batch absent from the response get a {@code CICError("Missing in CIC
     * response")}.</li>
     * </ul>
     * When {@code saveDocument} is {@code true}, each modified doc is reassigned via
     * {@code doc = session.saveDocument(doc);}. Between batches (only when more batches remain),
     * {@code session.save()} + a transaction commit/restart cycle are issued to keep the
     * transaction bounded.
     * <p>
     * When {@code saveDocument} is {@code false}, the caller owns persistence (including any
     * {@code CICError} markers written in memory; errors are also logged so no information is
     * silently lost).
     *
     * @param session              core session
     * @param docs                 input list; returned mutated in place (same reference)
     * @param configName           KE contribution name; falls back to {@code "default"} when blank
     * @param instructionsV2JsonStr optional v2 instructions JSON object string for this action
     * @param saveDocument         when {@code true}, save each document after writing
     * @param batchSize            batch size; {@code <= 0} uses {@link HylandKEService#getDefaultBatchSize()}
     * @return the same {@code docs} reference, mutated in place
     * @since 2025.16
     */
    public DocumentModelList runForDocuments(CoreSession session, DocumentModelList docs, String configName,
            String instructionsV2JsonStr, boolean saveDocument, int batchSize) {

        if (docs == null) {
            throw new NuxeoException("Input document list is required");
        }
        if (docs.isEmpty()) {
            return docs;
        }

        HylandKEService ke = Framework.getService(HylandKEService.class);
        CICEnrichmentHelper helper = Framework.getService(CICEnrichmentHelper.class);

        int defaultBatch = ke.getDefaultBatchSize();
        int effectiveBatch;
        if (batchSize <= 0) {
            effectiveBatch = defaultBatch;
        } else {
            effectiveBatch = batchSize;
            if (batchSize > defaultBatch) {
                LOG.warn("Requested batchSize ({}) is greater than the configured default ({}). Honoring the request.",
                        batchSize, defaultBatch);
            }
        }

        String extra = ServicesUtils.addInstructionsToExtraPayload(instructionsV2JsonStr, null);
        String effectiveConfig = StringUtils.isBlank(configName) ? null : configName;

        int total = docs.size();
        int fromIndex = 0;
        while (fromIndex < total) {
            int toIndex = Math.min(fromIndex + effectiveBatch, total);
            List<DocumentModel> batch = docs.subList(fromIndex, toIndex);

            processBatch(session, batch, effectiveConfig, extra, saveDocument, ke, helper);

            boolean moreBatches = toIndex < total;
            if (moreBatches) {
                // Mandatory inter-batch commit (keeps the transaction bounded).
                session.save();
                TransactionHelper.commitOrRollbackTransaction();
                TransactionHelper.startTransaction();
            }
            fromIndex = toIndex;
        }

        return docs;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected void processBatch(CoreSession session, List<DocumentModel> batch, String configName, String extra,
            boolean saveDocument, HylandKEService ke, CICEnrichmentHelper helper) {

        // Build payload list (only docs with a blob); keep sourceId -> DocumentModel mapping.
        // Use LinkedHashMap to preserve insertion order (helps logs and debugging).
        Map<String, DocumentModel> bySourceId = new LinkedHashMap<>();
        List<ContentToProcess> contentObjects = new ArrayList<>();
        for (DocumentModel doc : batch) {
            if (doc == null) {
                continue;
            }
            Blob blob = getBlob(doc);
            if (blob == null) {
                // getBlob may already have recorded a CICError (e.g. image ops record "Picture view
                // not found" via the service). If not, fall back to a generic "No blob".
                if (!doc.hasFacet(CIC_ERROR_FACET)) {
                    ke.setCICError(doc, HylandKEService.SERVICE_LABEL, 0, "No blob",
                            "No blob available for action " + getActionName() + " on " + doc.getId(), null);
                }
                if (saveDocument) {
                    session.saveDocument(doc);
                }
                continue;
            }
            String sourceId = doc.getId();
            bySourceId.put(sourceId, doc);
            contentObjects.add(new ContentToProcess(sourceId, blob));
        }

        if (contentObjects.isEmpty()) {
            return;
        }

        ServiceCallResult result;
        try {
            result = ke.enrich(configName, contentObjects, List.of(getActionName()), getClasses(),
                    getSimilarMetadataJsonArrayStr(), extra);
        } catch (IOException e) {
            String msg = "IO error calling KE: " + e.getMessage();
            LOG.warn("KE batch failed (IO): {}", e.getMessage(), e);
            failBatch(session, bySourceId, ke, 0, "IO error calling KE", msg, null, saveDocument);
            return;
        }

        if (result.getResponseCode() != 200) {
            String fullJson = result.toJsonString();
            LOG.warn("KE batch failed (HTTP {}): {}", result.getResponseCode(), result.getResponseMessage());
            failBatch(session, bySourceId, ke, result.getResponseCode(), "KE call failed",
                    result.getResponseMessage(), fullJson, saveDocument);
            return;
        }

        JSONObject envelope = helper.parseEnrichmentResponse(result.toJsonString());
        if (envelope == null) {
            String fullJson = result.toJsonString();
            LOG.warn("KE batch failed: could not parse envelope");
            failBatch(session, bySourceId, ke, 200, "Invalid envelope", "Could not parse KE envelope", fullJson,
                    saveDocument);
            return;
        }
        JSONObject response = envelope.optJSONObject("response");
        String status = response == null ? null : response.optString("status", null);
        if (response == null || !"SUCCESS".equals(status)) {
            String fullJson = result.toJsonString();
            LOG.warn("KE batch returned status={} (not SUCCESS)", status);
            failBatch(session, bySourceId, ke, 200, "KE response not SUCCESS", "status=" + status, fullJson,
                    saveDocument);
            return;
        }

        JSONArray results = response.optJSONArray("results");
        JSONArray mapping = result.getObjectKeysMapping();
        // objectKey -> sourceId
        Map<String, String> objectKeyToSourceId = new HashMap<>();
        if (mapping != null) {
            for (int i = 0; i < mapping.length(); i++) {
                JSONObject entry = mapping.optJSONObject(i);
                if (entry == null) {
                    continue;
                }
                String objectKey = entry.optString("objectKey", null);
                String sourceId = entry.optString("sourceId", null);
                if (objectKey != null && sourceId != null) {
                    objectKeyToSourceId.put(objectKey, sourceId);
                }
            }
        }

        // Track which sourceIds got a result so we can mark missing ones at the end.
        java.util.Set<String> seenSourceIds = new java.util.HashSet<>();
        String fullJson = result.toJsonString();

        if (results != null) {
            for (int i = 0; i < results.length(); i++) {
                JSONObject resultEntry = results.optJSONObject(i);
                if (resultEntry == null) {
                    continue;
                }
                String objectKey = resultEntry.optString("objectKey", null);
                String sourceId = objectKey == null ? null : objectKeyToSourceId.get(objectKey);
                if (sourceId == null) {
                    LOG.warn("Orphan KE result entry (no matching sourceId for objectKey={})", objectKey);
                    continue;
                }
                DocumentModel doc = bySourceId.get(sourceId);
                if (doc == null) {
                    LOG.warn("Orphan KE result entry (sourceId={} not in current batch)", sourceId);
                    continue;
                }
                seenSourceIds.add(sourceId);

                JSONObject actionWrapper = resultEntry.optJSONObject(getResultKey());
                if (actionWrapper == null) {
                    ke.setCICError(doc, HylandKEService.SERVICE_LABEL, 200, "Missing action result",
                            "Result key not found: " + getResultKey(), fullJson);
                    persistIfNeeded(session, doc, saveDocument);
                    continue;
                }
                Object actionError = actionWrapper.opt("error");
                if (actionError != null && actionError != JSONObject.NULL
                        && !String.valueOf(actionError).isEmpty()) {
                    ke.setCICError(doc, HylandKEService.SERVICE_LABEL, 200, "Action error",
                            String.valueOf(actionError), fullJson);
                    persistIfNeeded(session, doc, saveDocument);
                    continue;
                }
                Object actionResult = actionWrapper.opt("result");
                if (actionResult == null || actionResult == JSONObject.NULL) {
                    ke.setCICError(doc, HylandKEService.SERVICE_LABEL, 200, "Empty action result",
                            "Action returned no result", fullJson);
                    persistIfNeeded(session, doc, saveDocument);
                    continue;
                }
                try {
                    applyResult(doc, actionResult);
                    ke.clearCICError(doc);
                } catch (RuntimeException ex) {
                    LOG.warn("applyResult failed for action {} on doc {}: {}", getActionName(), doc.getId(),
                            ex.getMessage(), ex);
                    ke.setCICError(doc, HylandKEService.SERVICE_LABEL, 200, "Failed writing result",
                            ex.getMessage(), fullJson);
                }
                persistIfNeeded(session, doc, saveDocument);
            }
        }

        // Mark missing docs (sent in payload but absent from results)
        for (Map.Entry<String, DocumentModel> e : bySourceId.entrySet()) {
            if (!seenSourceIds.contains(e.getKey())) {
                DocumentModel doc = e.getValue();
                LOG.warn("Doc {} is missing from KE response", doc.getId());
                ke.setCICError(doc, HylandKEService.SERVICE_LABEL, 200, "Missing in CIC response",
                        "Doc " + doc.getId() + " absent from response.results", fullJson);
                persistIfNeeded(session, doc, saveDocument);
            }
        }
    }

    /** Mark every payload-eligible doc in the batch with a CICError (used for batch-level failures). */
    protected void failBatch(CoreSession session, Map<String, DocumentModel> bySourceId, HylandKEService ke,
            int responseCode, String shortMessage, String fullMessage, String fullJson, boolean saveDocument) {
        for (DocumentModel doc : bySourceId.values()) {
            ke.setCICError(doc, HylandKEService.SERVICE_LABEL, responseCode, shortMessage, fullMessage, fullJson);
            persistIfNeeded(session, doc, saveDocument);
        }
    }

    protected void persistIfNeeded(CoreSession session, DocumentModel doc, boolean saveDocument) {
        if (saveDocument) {
            session.saveDocument(doc);
        }
    }

    /** Local copy of the {@code CICError} facet name to avoid coupling to the impl class. */
    protected static final String CIC_ERROR_FACET = "CICError";

    /* ==================== Async dispatch (runAsynchronously=true) ==================== */

    /**
     * Schedules a {@link CICEnrichmentWork} for a single document and returns immediately. The
     * concrete op subclass is rebuilt inside the Work via reflection and its {@code @Param} fields
     * are restored from {@code paramsJson} through {@link #applyAsyncParams(JSONObject)}.
     * <p>
     * Persistence is always forced to {@code saveDocument=true} inside the Work — the caller's
     * value is ignored because asynchronous callers have no way to see the resulting documents.
     * The caller's choice is logged once (WARN) when it was {@code false}.
     *
     * @param session  core session (used only to read the repository name)
     * @param doc      the input document
     * @param paramsJson JSON object string holding the original {@code @Param} values
     * @since 2025.16
     */
    public void scheduleAsyncForDocument(CoreSession session, DocumentModel doc, JSONObject paramsJson) {
        if (doc == null) {
            throw new NuxeoException("Input document is required");
        }
        warnIfSaveDocumentFalse(paramsJson);
        WorkManager wm = Framework.getService(WorkManager.class);
        CICEnrichmentWork work = new CICEnrichmentWork(session.getRepositoryName(), List.of(doc.getId()),
                getClass().getName(), paramsJson.toString(), false);
        wm.schedule(work, true);
        LOG.info("Scheduled CICEnrichmentWork (single doc {}) for op {}", doc.getId(), getClass().getName());
    }

    /**
     * Schedules a {@link CICEnrichmentWork} for a list of documents and returns immediately. The
     * Work runs the standard batched {@link #runForDocuments} code path (so batching, inter-batch
     * commits and per-doc {@code CICError} marking are unchanged).
     *
     * @param session  core session (used only to read the repository name)
     * @param docs     the input list
     * @param paramsJson JSON object string holding the original {@code @Param} values
     * @since 2025.16
     */
    public void scheduleAsyncForDocuments(CoreSession session, DocumentModelList docs, JSONObject paramsJson) {
        if (docs == null || docs.isEmpty()) {
            return;
        }
        warnIfSaveDocumentFalse(paramsJson);
        List<String> ids = new ArrayList<>(docs.size());
        for (DocumentModel d : docs) {
            if (d != null) {
                ids.add(d.getId());
            }
        }
        WorkManager wm = Framework.getService(WorkManager.class);
        CICEnrichmentWork work = new CICEnrichmentWork(session.getRepositoryName(), ids, getClass().getName(),
                paramsJson.toString(), true);
        wm.schedule(work, true);
        LOG.info("Scheduled CICEnrichmentWork ({} docs) for op {}", ids.size(), getClass().getName());
    }

    /**
     * Logs a one-shot WARN when {@code saveDocument=false} was passed together with
     * {@code runAsynchronously=true}. Async callers cannot inspect the returned document(s), so
     * the Work always saves regardless of this flag.
     */
    protected void warnIfSaveDocumentFalse(JSONObject paramsJson) {
        if (paramsJson.has("saveDocument") && !paramsJson.optBoolean("saveDocument", false)) {
            LOG.warn(
                    "{} called with runAsynchronously=true and saveDocument=false. saveDocument is forced to true inside the background Work (async callers cannot read the result).",
                    getClass().getSimpleName());
        }
    }

    /**
     * Reapplies the {@code @Param} values captured at scheduling time onto a freshly instantiated
     * op (used by {@link CICEnrichmentWork} when running in the background). The default
     * implementation handles the params declared on this base class. Subclasses with additional
     * {@code @Param} fields (e.g. {@code renditionName}, {@code xpath}, {@code classes}) MUST
     * override this method, call {@code super.applyAsyncParams(params)}, then set their own
     * fields from {@code params}.
     * <p>
     * Reflection is intentionally NOT used here: each subclass owns its parameter contract.
     *
     * @param params the JSON object built by the original op call
     * @since 2025.16
     */
    public void applyAsyncParams(JSONObject params) {
        // Base class: no fields to restore. Subclasses override and set their @Param fields.
    }

    /**
     * Convenience helper for subclasses: builds the params JSON skeleton with the params declared
     * on the abstract base class ({@code configName}, {@code instructionsV2JsonStr},
     * {@code saveDocument}, {@code batchSize}, {@code runAsynchronously}). Subclasses append their
     * own fields.
     *
     * @since 2025.16
     */
    public static JSONObject baseParamsJson(String configName, String instructionsV2JsonStr, boolean saveDocument,
            int batchSize) {
        JSONObject json = new JSONObject();
        if (configName != null) {
            json.put("configName", configName);
        }
        if (instructionsV2JsonStr != null) {
            json.put("instructionsV2JsonStr", instructionsV2JsonStr);
        }
        json.put("saveDocument", saveDocument);
        json.put("batchSize", batchSize);
        return json;
    }

}
