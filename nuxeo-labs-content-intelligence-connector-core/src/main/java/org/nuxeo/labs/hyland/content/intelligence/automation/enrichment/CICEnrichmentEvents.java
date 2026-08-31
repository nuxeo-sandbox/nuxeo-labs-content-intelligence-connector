/*
 * (C) Copyright 2026 Hyland (http://hyland.com/) and others.
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

/**
 * Constants for the asynchronous Knowledge Enrichment completion event.
 * <p>
 * The event {@link #CIC_CALL_KE_DONE} is fired by {@link CICEnrichmentWork} at the end of every
 * Knowledge Enrichment call performed asynchronously (i.e. when a {@code CIC.*} document
 * operation was invoked with {@code runAsynchronously=true}). Synchronous callers do NOT fire the
 * event by design.
 * <p>
 * <b>Per-batch semantics</b>: when the original input was a {@code DocumentModelList},
 * {@link AbstractCICEnrichmentOp#runForDocuments} processes the docs in sequential batches (see
 * {@link org.nuxeo.labs.hyland.content.intelligence.service.enrichment.HylandKEService#getDefaultBatchSize}).
 * One {@code cicCallKEDone} event is fired per batch (one per actual KE call). A 25-doc Work
 * with {@code batchSize=10} therefore fires 3 events. The single-doc path is the trivial 1-batch
 * case and fires exactly 1 event.
 * <p>
 * <b>Event context</b> (always a {@link org.nuxeo.ecm.core.event.impl.DocumentEventContext}):
 * <ul>
 * <li>Principal document = the FIRST document of the batch (so doctype-based filtering still
 * works for single-doc events; multi-doc listeners should rely on {@link #CTX_DOC_IDS}).</li>
 * <li>{@link #CTX_DOC_IDS} carries the full list of document UIDs covered by this batch.</li>
 * <li>{@link #CTX_RESPONSE_ENVELOPE_JSON} is a JSON STRING of the canonical response envelope
 * (Serializable; safe for async / clustered listeners).</li>
 * </ul>
 *
 * <b>Failure paths</b>: the event is fired even when the KE call fails or when no call is made
 * at all (e.g. document has no blob). Inspect {@link #CTX_RESPONSE_STATUS}:
 * <ul>
 * <li>{@code "SUCCESS"} — KE call returned and {@code response.status == "SUCCESS"}.</li>
 * <li>{@code "FAILURE"} — KE call returned but the envelope reports a failure (non-2xx, missing
 * envelope, status != SUCCESS, action-level error, or empty action result).</li>
 * <li>{@code "NO_CALL"} — no CIC call was made (e.g. the document had no usable blob); a
 * synthetic envelope is provided in {@link #CTX_RESPONSE_ENVELOPE_JSON}.</li>
 * </ul>
 *
 * @since 2025.20
 */
public final class CICEnrichmentEvents {

    /** Event name fired at the end of every asynchronous KE call (one per batch). */
    public static final String CIC_CALL_KE_DONE = "cicCallKEDone";

    /**
     * KE action key the operation requested (e.g. {@code "image-description"},
     * {@code "text-summarization"}, {@code "text-classification"}, {@code "image-classification"},
     * {@code "text-embeddings"}, {@code "image-embeddings"}, {@code "text-metadata-generation"},
     * {@code "image-metadata-generation"}, {@code "named-entities-from-text"},
     * {@code "named-entities-from-image"}). Same value as
     * {@link AbstractCICEnrichmentOp#getActionName()} on the executing op.
     */
    public static final String CTX_ACTION_NAME = "cicActionName";

    /** Fully-qualified class name of the {@code CIC.*} op that produced this event. */
    public static final String CTX_OP_CLASS_NAME = "cicOpClassName";

    /**
     * The {@code configName} parameter the op was invoked with (may be {@code null} when the
     * op fell back to {@code "default"}).
     */
    public static final String CTX_CONFIG_NAME = "cicConfigName";

    /**
     * Full list of document UIDs covered by this event's batch.
     * <p>
     * Type: {@code java.util.ArrayList<String>} (Serializable).
     * Single-doc path = list of size 1; multi-doc path = the docs in the current batch.
     */
    public static final String CTX_DOC_IDS = "cicDocIds";

    /**
     * Canonical CIC response envelope as a JSON string (e.g.
     * {@code {"responseCode":200,"responseMessage":"OK","response":{...}}}). Listeners parse on
     * demand. For the {@code "NO_CALL"} status this is a synthetic envelope (no real CIC call
     * was made).
     * <p>
     * Type: {@code String}.
     */
    public static final String CTX_RESPONSE_ENVELOPE_JSON = "cicResponseEnvelopeJson";

    /**
     * The HTTP-style response code from the CIC call, or {@code 0} when no call was made.
     * <p>
     * Type: {@code Integer}.
     */
    public static final String CTX_RESPONSE_CODE = "cicResponseCode";

    /**
     * One of {@code "SUCCESS"}, {@code "FAILURE"}, {@code "NO_CALL"} (see class Javadoc).
     * <p>
     * Type: {@code String}.
     */
    public static final String CTX_RESPONSE_STATUS = "cicResponseStatus";

    /**
     * Zero-based index of this batch within the Work. Always {@code 0} for the single-doc path.
     * <p>
     * Type: {@code Integer}.
     */
    public static final String CTX_BATCH_INDEX = "cicBatchIndex";

    /**
     * Total number of batches produced by the current Work (=1 for single-doc).
     * <p>
     * Type: {@code Integer}.
     */
    public static final String CTX_BATCH_COUNT = "cicBatchCount";

    /**
     * {@code true} when the original op input was a {@code DocumentModelList},
     * {@code false} when the input was a single {@code DocumentModel}.
     * <p>
     * Type: {@code Boolean}.
     */
    public static final String CTX_IS_LIST_INPUT = "cicIsListInput";

    private CICEnrichmentEvents() {
        // constants holder
    }
}
