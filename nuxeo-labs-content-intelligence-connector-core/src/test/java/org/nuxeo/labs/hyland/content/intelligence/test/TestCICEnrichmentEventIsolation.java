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

import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.labs.hyland.content.intelligence.automation.enrichment.CICEnrichmentWork;
import org.nuxeo.labs.hyland.content.intelligence.automation.enrichment.CICGetImageDescriptionOp;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

/**
 * Proves the {@code cicCallKEDone} rollback-isolation contract end to end: a deliberately hostile
 * <b>synchronous post-commit</b> listener (see {@link RollbackThrowingKEDoneListener}, deployed via
 * {@code test-ke-done-throwing-listener-contrib.xml}) marks its transaction rollback-only and throws,
 * yet the document enriched by the background {@code cicEnrichment} Work still keeps its changes.
 * <p>
 * Runs fully offline (no CIC credentials): the target document is a blob-less {@code File}, so the
 * image-description op takes the {@code NO_CALL} path — it records a {@code CICError} facet and saves
 * the document without ever calling the CIC platform. The event is still fired (async mode), the
 * listener still runs (and fails), and the {@code CICError} facet must survive because post-commit
 * listeners run AFTER the enrichment transaction has committed, each in its own separate transaction.
 */
@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@Deploy("nuxeo-hyland-content-intelligence-connector-core")
@Deploy("nuxeo-hyland-content-intelligence-connector-core:test-ke-done-throwing-listener-contrib.xml")
public class TestCICEnrichmentEventIsolation {

    @Inject
    protected CoreSession session;

    @Inject
    protected TransactionalFeature txFeature;

    @Test
    public void postCommitListenerFailureShouldNotRollBackEnrichment() throws InterruptedException {
        RollbackThrowingKEDoneListener.reset();

        // Blob-less File => the image op takes the NO_CALL path (records a CICError facet, saves,
        // no CIC HTTP call).
        DocumentModel doc = session.createDocumentModel("/", "isolation-doc", "File");
        doc = session.createDocument(doc);
        String docId = doc.getId();
        // Commit so the document is persisted and visible to the Work's own (system) session.
        txFeature.nextTransaction();

        // Schedule the async single-doc enrichment Work directly (same as scheduleAsyncForDocument).
        WorkManager wm = Framework.getService(WorkManager.class);
        CICEnrichmentWork work = new CICEnrichmentWork(session.getRepositoryName(), List.of(docId),
                CICGetImageDescriptionOp.class.getName(), "{}", false);
        wm.schedule(work);

        // Wait on all queues: the cicEnrichment category maps to the catch-all "default" queue in the
        // test runtime (no dedicated named queue is contributed), so awaiting it by id would NPE.
        boolean completed = wm.awaitCompletion(60, TimeUnit.SECONDS);
        assertTrue("cicEnrichment Work did not complete in time", completed);

        // The hostile post-commit listener must have fired (and failed internally).
        assertTrue("The cicCallKEDone listener never fired", RollbackThrowingKEDoneListener.FIRED_COUNT.get() > 0);

        // Despite the listener throwing + marking rollback-only, the enrichment must have survived.
        txFeature.nextTransaction();
        DocumentModel reloaded = session.getDocument(new IdRef(docId));
        assertTrue("Enrichment was rolled back by the failing post-commit listener — isolation broken",
                reloaded.hasFacet("CICError"));
    }
}
