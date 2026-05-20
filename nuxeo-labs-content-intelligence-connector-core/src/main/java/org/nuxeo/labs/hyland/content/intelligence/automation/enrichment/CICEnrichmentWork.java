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

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.work.AbstractWork;

/**
 * Generic background {@link org.nuxeo.ecm.core.work.api.Work Work} that executes a
 * {@code CIC.*} Knowledge Enrichment operation asynchronously.
 * <p>
 * Scheduled by {@link AbstractCICEnrichmentOp#scheduleAsync} when the caller passes
 * {@code runAsynchronously=true} on any of the {@code CIC.*} document operations. The Work:
 * <ul>
 * <li>Re-instantiates the concrete operation class via reflection.</li>
 * <li>Replays the original {@code @Param} values via
 * {@link AbstractCICEnrichmentOp#applyAsyncParams(JSONObject)}.</li>
 * <li>Calls {@link AbstractCICEnrichmentOp#runForDocument} (single) or
 * {@link AbstractCICEnrichmentOp#runForDocuments} (list) with {@code saveDocument=true} —
 * the caller's {@code saveDocument} value is intentionally overridden because the Work owns
 * persistence (the caller has no other way to see the resulting documents).</li>
 * </ul>
 * <p>
 * Errors are recorded on each document via the existing {@code CICError} facet by the same
 * code path that runs synchronously. The Work itself only fails when it cannot even load the
 * documents or instantiate the op class.
 * <p>
 * All instances run under the {@code cicEnrichment} category. Tune the queue size via
 * {@code nuxeo.works.queue.cicEnrichment.maxThreads} in {@code nuxeo.conf}.
 *
 * @since 2025.16
 */
public class CICEnrichmentWork extends AbstractWork {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LogManager.getLogger(CICEnrichmentWork.class);

    public static final String CATEGORY = "cicEnrichment";

    /** Fully-qualified class name of the concrete {@code CIC.*} op to run. */
    protected final String opClassName;

    /** Document ids to process (all in the same repository, see {@link #repositoryName}). */
    protected final List<String> docIds;

    /** Serialized params (the same {@code @Param} values that drove the original call). */
    protected final String paramsJson;

    /** True when the original input was a {@code DocumentModelList}. */
    protected final boolean isListInput;

    /**
     * @param repositoryName the doc repository
     * @param docIds         doc ids to process
     * @param opClassName    FQN of the {@link AbstractCICEnrichmentOp} subclass to run
     * @param paramsJson     JSON object string of the original {@code @Param} values
     * @param isListInput    {@code true} when the original input was a list (drives the batched
     *                       multi-doc code path); {@code false} for a single document
     */
    public CICEnrichmentWork(String repositoryName, List<String> docIds, String opClassName,
            String paramsJson, boolean isListInput) {
        this.repositoryName = repositoryName;
        this.docIds = List.copyOf(docIds);
        this.opClassName = opClassName;
        this.paramsJson = paramsJson;
        this.isListInput = isListInput;
        // Pinpoint the Work on the first doc (for the Admin Active Workers UI).
        if (!this.docIds.isEmpty()) {
            this.docId = this.docIds.get(0);
        }
    }

    @Override
    public String getCategory() {
        return CATEGORY;
    }

    @Override
    public String getTitle() {
        String simpleName = opClassName.substring(opClassName.lastIndexOf('.') + 1);
        return "CIC Enrichment: " + simpleName + " (" + docIds.size() + " doc"
                + (docIds.size() == 1 ? "" : "s") + ")";
    }

    @Override
    public int getRetryCount() {
        // Best-effort: a transient CIC failure will leave a CICError on the docs, no retry.
        return 0;
    }

    @Override
    public void work() {
        if (docIds.isEmpty()) {
            return;
        }

        AbstractCICEnrichmentOp op = instantiateOp();
        JSONObject params = new JSONObject(paramsJson == null ? "{}" : paramsJson);
        op.applyAsyncParams(params);

        String configName = params.optString("configName", null);
        String instructionsV2 = params.optString("instructionsV2JsonStr", null);
        int batchSize = params.optInt("batchSize", 0);

        openSystemSession();

        if (isListInput) {
            DocumentModelListImpl docs = new DocumentModelListImpl();
            for (String id : docIds) {
                DocumentRef ref = new IdRef(id);
                if (session.exists(ref)) {
                    docs.add(session.getDocument(ref));
                } else {
                    LOG.warn("CICEnrichmentWork: doc {} no longer exists, skipped", id);
                }
            }
            if (!docs.isEmpty()) {
                op.runForDocuments(session, docs, configName, instructionsV2, true, batchSize);
            }
        } else {
            // Single-doc path.
            String id = docIds.get(0);
            DocumentRef ref = new IdRef(id);
            if (!session.exists(ref)) {
                LOG.warn("CICEnrichmentWork: doc {} no longer exists, skipped", id);
                return;
            }
            DocumentModel doc = session.getDocument(ref);
            op.runForDocument(session, doc, configName, instructionsV2, true);
        }
    }

    /** Reflective instantiation of the {@code CIC.*} op subclass. */
    protected AbstractCICEnrichmentOp instantiateOp() {
        try {
            Class<?> clazz = Class.forName(opClassName);
            if (!AbstractCICEnrichmentOp.class.isAssignableFrom(clazz)) {
                throw new NuxeoException(
                        "Class " + opClassName + " is not an AbstractCICEnrichmentOp subclass");
            }
            return (AbstractCICEnrichmentOp) clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new NuxeoException("Failed to instantiate CIC op " + opClassName, e);
        }
    }

    /**
     * Returns a defensive copy of the doc id list. Provided for tests / monitoring.
     */
    public List<String> getDocIds() {
        return new ArrayList<>(docIds);
    }

    public String getOpClassName() {
        return opClassName;
    }
}
