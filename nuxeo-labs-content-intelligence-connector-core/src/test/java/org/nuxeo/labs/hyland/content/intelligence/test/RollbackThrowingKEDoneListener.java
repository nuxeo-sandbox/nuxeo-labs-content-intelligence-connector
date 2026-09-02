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
 * Deliberately hostile listener on {@code cicCallKEDone}, used by
 * {@link TestCICEnrichmentEventIsolation} to prove rollback isolation.
 * <p>
 * For every {@code cicCallKEDone} event it increments a static counter, marks the current
 * transaction rollback-only, then throws.
 * <p>
 * Since 2025.21 the firing side commits the enrichment transaction just BEFORE building the event
 * context, so none of this can roll back the already-enriched documents — whatever the listener
 * declaration. It happens to implement {@link PostCommitEventListener} here, but the test outcome no
 * longer depends on that: an inline {@code EventListener} would be just as harmless.
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
