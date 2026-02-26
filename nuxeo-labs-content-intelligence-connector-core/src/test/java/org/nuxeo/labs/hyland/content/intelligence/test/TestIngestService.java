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
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.labs.hyland.content.intelligence.service.ingest.IngestService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import jakarta.inject.Inject;

/**
 * @since TODO
 */
@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class, ConfigCheckerFeature.class })
@Deploy("nuxeo-hyland-content-intelligence-connector-core")
public class TestIngestService {

    @Inject
    protected CoreSession session;

    @Inject
    protected IngestService ingestService;

    @Test
    public void testServiceIsDeployed() {
        assertNotNull(ingestService);
    }

    @Test
    public void testHasAtLeastOneContribution() {

        assertTrue(ingestService.getContribNames().size() > 0);

    }

    // ==================================================
    // ==================================================
    // These tests are to be ran locally and check results
    // until there are some vali unit test repo, doc, etc.
    // => Henc why they don't assert anything
    // ==================================================
    // ==================================================
    @Test
    public void shouldNotFindDocument_ingest() {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasIngestClientInfo());

        String sourceId = System.getenv(ConfigCheckerFeature.ENV_CIC_INGEST_UNIT_TEST_REPO_SOURCE);
        Assume.assumeTrue("No sourceId repo set in env. variables => ignoring the test",
                StringUtils.isNotBlank(sourceId));

        String DOC_ID = "whatever";
        String DIGEST = "whatever";
        // Using unexistant doc in the ContentLake
        ServiceCallResult result = ingestService.checkDigest(null, DOC_ID, DIGEST, sourceId);
        if (result.getResponseCode() == 404) {
            System.out.println("TestIngestService#shouldNotFindDocument_ingest => SUCCESS");
        } else {
            System.out.println("TestIngestService#shouldNotFindDocument_ingest => FAILURE");
        }
    }

    @Test
    public void testHasDocumentAndBlob() {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasIngestClientInfo());

        String sourceId = System.getenv(ConfigCheckerFeature.ENV_CIC_INGEST_UNIT_TEST_REPO_SOURCE);
        Assume.assumeTrue("No sourceId repo set in env. variables => ignoring the test",
                StringUtils.isNotBlank(sourceId));

        // Valid doc ID and digest
        String DOC_ID = "3121a5b9-4c0a-400a-b9ab-b49dd5a8c95d";
        String digest = "f09b5e9444cc2cc845c9a2e11225d7d2";
        ServiceCallResult result = ingestService.checkDigest(null, DOC_ID, digest, sourceId);
        JSONObject responseJson = result.getResponseAsJSONObject();
        boolean goOn = false;
        try {
            if (result.getResponseCode() == 200) {
                if (responseJson.getBoolean("exists")) {
                    System.out.println("TestIngestService#testHasDocumentAndBlob-1 => SUCCESS");
                    goOn = true;
                } else {
                    System.out.println(
                            "TestIngestService#testHasDocumentAndBlob-1 => FAILURE (exists should be false)");
                }
            } else {
                System.out.println("TestIngestService#testHasDocumentAndBlob-1 => FAILURE with responseCode "
                        + result.getResponseCode());
            }
        } catch (Exception e) {
            //
        } finally {
            if (!goOn) {
                return;
            }
        }

        // Invalid digest
        digest = "abcdef1234567890abcdef1234567890";
        result = ingestService.checkDigest(null, DOC_ID, digest, sourceId);
        responseJson = result.getResponseAsJSONObject();
        goOn = false;
        if (result.getResponseCode() == 200) {
            if (!responseJson.getBoolean("exists")) {
                System.out.println("TestIngestService#testHasDocumentAndBlob-2 => SUCCESS");
                goOn = true;
            } else {
                System.out.println(
                        "TestIngestService#testHasDocumentAndBlob-2 => FAILURE (exists should be false)");
            }
        } else {
            System.out.println("TestIngestService#testHasDocumentAndBlob-2 => FAILURE with responseCode "
                    + result.getResponseCode());
        }
    }

}
