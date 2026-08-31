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
package org.nuxeo.labs.hyland.content.intelligence.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.inject.Inject;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.labs.hyland.content.intelligence.automation.enrichment.AbstractCICEnrichmentOp;
import org.nuxeo.labs.hyland.content.intelligence.automation.enrichment.AbstractCICEnrichmentOp.BatchOutcome;
import org.nuxeo.labs.hyland.content.intelligence.automation.enrichment.CICGetImageDescriptionOp;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * Verifies the {@link BatchOutcome} contract that drives the {@code cicCallKEDone} event fired
 * by {@code CICEnrichmentWork} (see {@code CICEnrichmentEvents}).
 * <p>
 * These tests bypass the {@code WorkManager} and call
 * {@link AbstractCICEnrichmentOp#runForDocument(CoreSession, DocumentModel, String, String, boolean, java.util.function.Consumer)}
 * directly with a capturing consumer. This isolates the per-batch outcome construction (single
 * return point, statuses {@code SUCCESS} / {@code FAILURE} / {@code NO_CALL}, doc UID list,
 * synthetic envelope JSON for the no-call paths) from the event-firing wiring in
 * {@code CICEnrichmentWork}, which is a thin pass-through to {@code EventProducer.fireEvent}.
 * <p>
 * Runs offline (no CIC credentials required); exercises the {@code NO_CALL} branch via a
 * {@code File} document with no {@code file:content} blob.
 */
@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@Deploy("nuxeo-hyland-content-intelligence-connector-core")
public class TestCICEnrichmentEvent {

    @Inject
    protected CoreSession session;

    @Test
    public void shouldFireOutcomeWithNoCallStatusForBlobLessSingleDoc() {
        DocumentModel doc = session.createDocumentModel("/", "doc-no-blob", "File");
        doc = session.createDocument(doc);

        // Capture the BatchOutcome the run-method emits.
        List<BatchOutcome> captured = new CopyOnWriteArrayList<>();
        AbstractCICEnrichmentOp op = new CICGetImageDescriptionOp();

        op.runForDocument(session, doc, /* configName */ null, /* instructionsV2JsonStr */ null,
                /* saveDocument */ true, captured::add);

        assertEquals("Expected exactly one BatchOutcome for the single-doc path", 1, captured.size());
        BatchOutcome outcome = captured.get(0);

        // No CIC call was made (doc has no blob the image op can use).
        assertEquals("NO_CALL", outcome.responseStatus);
        assertEquals(0, outcome.responseCode);

        // docIds list contains the single doc's UID.
        assertNotNull(outcome.docIds);
        assertEquals(1, outcome.docIds.size());
        assertEquals(doc.getId(), outcome.docIds.get(0));

        // Single-doc path = batch 0 of 1.
        assertEquals(0, outcome.batchIndex);
        assertEquals(1, outcome.batchCount);

        // Synthetic envelope is a parseable JSON string carrying the canonical envelope shape.
        assertNotNull(outcome.responseEnvelopeJson);
        JSONObject envelope = new JSONObject(outcome.responseEnvelopeJson);
        assertTrue("envelope should carry responseCode", envelope.has("responseCode"));
        assertEquals(0, envelope.getInt("responseCode"));
        assertTrue("envelope should carry responseMessage", envelope.has("responseMessage"));
    }

    @Test
    public void shouldFireOneOutcomePerBatchForBlobLessMultiDoc() {
        // 5 docs, no blobs => single batch (default batch size >= 5), NO_CALL outcome.
        DocumentModelListImpl docs = new DocumentModelListImpl();
        for (int i = 0; i < 5; i++) {
            DocumentModel d = session.createDocumentModel("/", "doc-list-" + i, "File");
            d = session.createDocument(d);
            docs.add(d);
        }

        List<BatchOutcome> captured = new CopyOnWriteArrayList<>();
        AbstractCICEnrichmentOp op = new CICGetImageDescriptionOp();

        op.runForDocuments(session, docs, /* configName */ null, /* instructionsV2JsonStr */ null,
                /* saveDocument */ true, /* batchSize */ 0, captured::add);

        assertEquals("Expected exactly one BatchOutcome (single batch of 5 blob-less docs)",
                1, captured.size());
        BatchOutcome outcome = captured.get(0);

        assertEquals("NO_CALL", outcome.responseStatus);
        assertEquals(0, outcome.responseCode);
        assertEquals(0, outcome.batchIndex);
        assertEquals(1, outcome.batchCount);

        // batchDocIds reflects ALL docs in the batch (including blob-less ones).
        assertEquals(5, outcome.docIds.size());
        List<String> expected = new ArrayList<>();
        for (DocumentModel d : docs) {
            expected.add(d.getId());
        }
        assertEquals(expected, outcome.docIds);

        // Synthetic envelope is parseable.
        JSONObject envelope = new JSONObject(outcome.responseEnvelopeJson);
        assertEquals(0, envelope.getInt("responseCode"));
    }

    @Test
    public void shouldFireMultipleOutcomesWhenBatchSizeForcesSplit() {
        // 7 docs with batchSize=3 => ceil(7/3)=3 batches: sizes 3, 3, 1.
        DocumentModelListImpl docs = new DocumentModelListImpl();
        for (int i = 0; i < 7; i++) {
            DocumentModel d = session.createDocumentModel("/", "doc-split-" + i, "File");
            d = session.createDocument(d);
            docs.add(d);
        }

        List<BatchOutcome> captured = new CopyOnWriteArrayList<>();
        AbstractCICEnrichmentOp op = new CICGetImageDescriptionOp();

        op.runForDocuments(session, docs, null, null, true, /* batchSize */ 3, captured::add);

        assertEquals("Expected 3 outcomes (batches of 3, 3, 1)", 3, captured.size());

        // Sizes
        assertEquals(3, captured.get(0).docIds.size());
        assertEquals(3, captured.get(1).docIds.size());
        assertEquals(1, captured.get(2).docIds.size());

        // Indices and counts.
        assertEquals(0, captured.get(0).batchIndex);
        assertEquals(1, captured.get(1).batchIndex);
        assertEquals(2, captured.get(2).batchIndex);
        assertEquals(3, captured.get(0).batchCount);
        assertEquals(3, captured.get(1).batchCount);
        assertEquals(3, captured.get(2).batchCount);

        // All NO_CALL (no blobs).
        for (BatchOutcome outcome : captured) {
            assertEquals("NO_CALL", outcome.responseStatus);
            assertEquals(0, outcome.responseCode);
        }
    }

    @Test
    public void legacyOverloadShouldNotFailWithoutConsumer() {
        // The 5-arg / 6-arg overloads pass a NOOP_BATCH_OUTCOME_CONSUMER under the hood.
        // Sanity-check: invoking them on a blob-less doc does not throw.
        DocumentModel doc = session.createDocumentModel("/", "doc-legacy", "File");
        doc = session.createDocument(doc);

        AbstractCICEnrichmentOp op = new CICGetImageDescriptionOp();
        DocumentModel result = op.runForDocument(session, doc, null, null, true);
        assertNotNull(result);
        assertTrue("doc should carry CICError facet", result.hasFacet("CICError"));
    }
}
