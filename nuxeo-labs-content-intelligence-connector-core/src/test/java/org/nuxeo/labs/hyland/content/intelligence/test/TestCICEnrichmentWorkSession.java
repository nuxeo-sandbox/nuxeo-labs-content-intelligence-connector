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
 *     Thibaud Arguillere (With the help of Opencode/Claude Opus for the Web UI port from a Studio project)
 */
package org.nuxeo.labs.hyland.content.intelligence.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.ACP;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.labs.hyland.content.intelligence.automation.enrichment.CICEnrichmentWork;
import org.nuxeo.labs.hyland.content.intelligence.automation.enrichment.CICGetImageDescriptionOp;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import jakarta.inject.Inject;

/**
 * Verifies that {@link CICEnrichmentWork} runs under the identity of the user who scheduled it, and no longer
 * under a system session.
 * <p>
 * Runs fully offline: the target document is a blob-less {@code File}, so the image-description op takes the
 * {@code NO_CALL} path — it records a {@code CICError} facet and saves the document without ever calling the CIC
 * platform. That save is enough to observe which principal the Work acted as, through {@code dc:lastContributor}.
 *
 * @since 2025.22
 */
@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@Deploy("nuxeo-hyland-content-intelligence-connector-core")
public class TestCICEnrichmentWorkSession {

    protected static final String TEST_USER = "cic-test-user";

    @Inject
    protected CoreSession session;

    @Inject
    protected UserManager userManager;

    @Inject
    protected TransactionalFeature txFeature;

    protected void createTestUser() {
        if (userManager.getPrincipal(TEST_USER) != null) {
            return;
        }
        DocumentModel user = userManager.getBareUserModel();
        user.setPropertyValue(userManager.getUserSchemaName() + ":username", TEST_USER);
        userManager.createUser(user);
    }

    protected String createDocument(String name) {
        DocumentModel doc = session.createDocumentModel("/", name, "File");
        doc = session.createDocument(doc);
        session.save();
        return doc.getId();
    }

    protected void grant(String docId, String permission) {
        DocumentModel doc = session.getDocument(new IdRef(docId));
        ACP acp = doc.getACP();
        ACL acl = acp.getOrCreateACL(ACL.LOCAL_ACL);
        acl.add(new ACE(TEST_USER, permission, true));
        doc.setACP(acp, true);
        session.save();
    }

    protected void runWorkAs(String docId, String username) throws InterruptedException {
        WorkManager wm = Framework.getService(WorkManager.class);
        CICEnrichmentWork work = new CICEnrichmentWork(session.getRepositoryName(), List.of(docId),
                CICGetImageDescriptionOp.class.getName(), "{}", false);
        if (username != null) {
            work.setOriginatingUsername(username);
        }
        wm.schedule(work);
        assertTrue("cicEnrichment Work did not complete in time", wm.awaitCompletion(60, TimeUnit.SECONDS));
        txFeature.nextTransaction();
    }

    /**
     * The security property that matters: a user who may only READ a document must not be able to have it
     * modified by passing {@code runAsynchronously=true}.
     * <p>
     * This is the discriminating test. Asserting on {@code dc:lastContributor} would NOT work:
     * {@code openSystemSession()} passes the originating user name to
     * {@code CoreInstance.getCoreSessionSystem(...)}, so the contributor ends up identical in both modes. Only
     * ACL enforcement actually tells a user session apart from a system one.
     */
    @Test
    public void readOnlyUserShouldNotBeAbleToTriggerAWrite() throws InterruptedException {

        createTestUser();
        String docId = createDocument("work-session-readonly");
        grant(docId, SecurityConstants.READ);
        txFeature.nextTransaction();

        runWorkAs(docId, TEST_USER);

        DocumentModel reloaded = session.getDocument(new IdRef(docId));
        assertFalse("A READ-only user managed to have the document written by the async Work",
                reloaded.hasFacet("CICError"));
    }

    /**
     * Counterpart of the previous test: with write permission the enrichment must go through, otherwise the
     * change would simply have broken the feature for everybody.
     */
    @Test
    public void userWithWritePermissionShouldSucceed() throws InterruptedException {

        createTestUser();
        String docId = createDocument("work-session-readwrite");
        grant(docId, SecurityConstants.READ_WRITE);
        txFeature.nextTransaction();

        runWorkAs(docId, TEST_USER);

        DocumentModel reloaded = session.getDocument(new IdRef(docId));
        // The NO_CALL path recorded its error, so the Work really did write as that user.
        assertTrue("The Work did not write the CICError facet", reloaded.hasFacet("CICError"));
    }

    /**
     * A Work carrying no originating user name must still run, as system. This covers operations legitimately
     * called from a system context, and Works deserialized from a queue persisted by a pre-2025.22 version.
     */
    @Test
    public void workWithoutOriginatingUserShouldFallBackToSystem() throws InterruptedException {

        String docId = createDocument("work-session-system");
        txFeature.nextTransaction();

        runWorkAs(docId, null);

        DocumentModel reloaded = session.getDocument(new IdRef(docId));
        assertTrue("The Work did not write the CICError facet", reloaded.hasFacet("CICError"));
    }

}
