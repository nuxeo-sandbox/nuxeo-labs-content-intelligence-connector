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

import static org.nuxeo.labs.hyland.content.intelligence.automation.enrichment.CICEnrichmentEvents.CIC_CALL_KE_DONE;

import java.util.concurrent.atomic.AtomicInteger;

import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.PostCommitEventListener;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * Deliberately hostile <b>synchronous post-commit</b> listener on {@code cicCallKEDone}, used by
 * {@link TestCICEnrichmentEventIsolation} to prove rollback isolation.
 * <p>
 * It implements {@link PostCommitEventListener} (NOT {@code EventListener}) so the runtime registers
 * it as a post-commit listener — the interface, not the {@code postCommit="true"} attribute, is what
 * makes a class-based listener post-commit. For every {@code cicCallKEDone} event it increments a
 * static counter, marks the current (post-commit) transaction rollback-only, then throws.
 * <p>
 * Because post-commit listeners run AFTER the enrichment transaction has committed and in their own
 * separate transaction, none of this can roll back the already-enriched documents.
 */
public class RollbackThrowingKEDoneListener implements PostCommitEventListener {

    /** Number of {@code cicCallKEDone} events this listener has handled (across the JVM). */
    public static final AtomicInteger FIRED_COUNT = new AtomicInteger(0);

    /** Resets the counter between tests. */
    public static void reset() {
        FIRED_COUNT.set(0);
    }

    @Override
    public void handleEvent(EventBundle events) {
        for (Event event : events) {
            if (!CIC_CALL_KE_DONE.equals(event.getName())) {
                continue;
            }
            FIRED_COUNT.incrementAndGet();
            // Both hostile mechanisms at once: poison this listener's own transaction and throw.
            TransactionHelper.setTransactionRollbackOnly();
            throw new RuntimeException("Deliberate failure from RollbackThrowingKEDoneListener");
        }
    }
}
