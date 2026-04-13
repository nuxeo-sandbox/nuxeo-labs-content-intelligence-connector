/*
 * (C) Copyright 2026 Hyland (http://hyland.com/)  and others.
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.labs.hyland.content.intelligence.service.contentlake.ContentLakeService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import javax.inject.Inject;

/**
 * @since TODO
 */
@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class, ConfigCheckerFeature.class })
@Deploy("nuxeo-hyland-content-intelligence-connector-core")
public class TestContentLakeService {

    @Inject
    protected CoreSession session;

    @Inject
    protected ContentLakeService clService;

    @Test
    public void testServiceIsDeployed() {
        assertNotNull(clService);
    }

    @Test
    public void testHasAtLeastOneContribution() {

        assertTrue(clService.getContribNames().size() > 0);

    }

    // ==================================================
    // ==================================================
    // These tests are to be ran locally and check results
    // until there are some vali unit test repo, doc, etc.
    // => Henc why they don't assert anything
    // ==================================================
    // ==================================================
    @Test
    public void shouldFindDocument() {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasContentLakeClientInfo());

        String sourceId = System.getenv(ConfigCheckerFeature.ENV_CIC_CONTENTLAKE_UNIT_TEST_REPO_SOURCE);
        Assume.assumeTrue("No sourceId repo set in env. variables => ignoring the test",
                StringUtils.isNotBlank(sourceId));

        String DOC_ID = "3121a5b9-4c0a-400a-b9ab-b49dd5a8c95d";
        ServiceCallResult result = clService.getDocument(null, DOC_ID, sourceId);
        if (result.getResponseCode() == 200) {
            System.out.println("TestContentLakeService#shouldFindDocument => SUCCESS");
        } else {
            System.out.println("TestContentLakeService#shouldFindDocument => FAILURE\n" + result.toJsonString(2));
        }
    }
    
    @Test
    public void shouldFailOnWrongSourceId() {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasContentLakeClientInfo());

        String DOC_ID = "3121a5b9-4c0a-400a-b9ab-b49dd5a8c95d";
        ServiceCallResult result = clService.getDocument(null, DOC_ID, "abc");
        if (result.getResponseCode() == 404) {
            System.out.println("TestContentLakeService#shouldFailOnWrongSourceId => SUCCESS");
        } else {
            System.out.println("TestContentLakeService#shouldFailOnWrongSourceId => FAILURE\n" + result.toJsonString(2));
        }
    }
    
    @Test
    public void shouldFailOnWrongDocId() {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasContentLakeClientInfo());

        String sourceId = System.getenv(ConfigCheckerFeature.ENV_CIC_CONTENTLAKE_UNIT_TEST_REPO_SOURCE);
        Assume.assumeTrue("No sourceId repo set in env. variables => ignoring the test",
                StringUtils.isNotBlank(sourceId));

        String DOC_ID = "whatever";
        ServiceCallResult result = clService.getDocument(null, DOC_ID, "abc");
        if (result.getResponseCode() == 404) {
            System.out.println("TestContentLakeService#shouldFailOnWrongSourceId => SUCCESS");
        } else {
            System.out.println("TestContentLakeService#shouldFailOnWrongSourceId => FAILURE\n" + result.toJsonString(2));
        }
    }

}
