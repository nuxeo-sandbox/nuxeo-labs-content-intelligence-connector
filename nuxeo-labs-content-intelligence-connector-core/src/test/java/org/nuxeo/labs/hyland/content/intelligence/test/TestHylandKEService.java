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
package org.nuxeo.labs.hyland.content.intelligence.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.labs.hyland.content.intelligence.ContentToProcess;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.HylandKEService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class, ConfigCheckerFeature.class })
@Deploy("nuxeo-hyland-content-intelligence-connector-core")
public class TestHylandKEService {

    public static final String TEST_IMAGE_PATH = "files/dc-3-smaller.jpg";

    public static final String TEST_OTHER_IMAGE_PATH = "files/musubimaru.png";

    public static final String TEST_CONTRACT_PATH = "files/samplecontract.pdf";

    public static final String TEST_CONTRACT_MIMETYPE = "application/pdf";

    public static final String TEST_IMAGE_MIMETYPE = "image/jpeg";

    public static final String TEST_OTHER_IMAGE_MIMETYPE = "image/png";

    @Inject
    protected HylandKEService hylandKEService;

    @Before
    public void onceExecutedBeforeAll() throws Exception {

        // Actually, nothing to do here.
    }

    @Test
    public void testServiceIsDeployed() {
        assertNotNull(hylandKEService);
    }

    @Test
    public void shouldReturn404OnBadEndPoint() {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasEnrichmentClientInfo());

        ServiceCallResult result = hylandKEService.invokeEnrichment(null, "GET", "/INVALID_END_POINT", null);

        assertNotNull(result);

        int responseCode = result.getResponseCode();
        assertEquals(responseCode, 404);
    }

    @Test
    public void canGetContentProcessActions() {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasEnrichmentClientInfo());

        ServiceCallResult result = hylandKEService.invokeEnrichment(null, "GET", "/api/content/process/actions", null);

        assertNotNull(result);

        assertTrue(result.callResponseOK());

        JSONArray actions = result.getResponseAsJSONArray();
        assertNotNull(actions);
        assertTrue(actions.length() > 0);
    }

    @Test
    public void shouldGetPresignedUrl() {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasEnrichmentClientInfo());

        ServiceCallResult result = hylandKEService.invokeEnrichment(null, "GET",
                "/api/files/upload/presigned-url?contentType=" + TEST_IMAGE_MIMETYPE.replace("/", "%2F"), null);
        assertNotNull(result);

        assertTrue(result.callResponseOK());

        JSONObject responseJson = result.getResponseAsJSONObject();
        assertNotNull(result);

        String presignedUrl = responseJson.getString("presignedUrl");
        assertNotNull(presignedUrl);

        String objectKey = responseJson.getString("objectKey");
        assertNotNull(objectKey);

    }

    @Test
    public void shouldSendThenGetResults() throws Exception {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasEnrichmentClientInfo());

        // 1. Send file
        File f = FileUtils.getResourceFileFromContext(TEST_IMAGE_PATH);
        ServiceCallResult result = hylandKEService.sendForEnrichment(null, f, null, TEST_IMAGE_MIMETYPE,
                List.of("image-description"), null, null, null);
        assertNotNull(result);

        assertTrue(result.callResponseOK());

        JSONObject responseJson = result.getResponseAsJSONObject();
        String jobId = responseJson.getString("processingId");
        assertNotNull(jobId);

        // 2. Wait a bit (we should try in another thread, but, well)
        java.lang.Thread.sleep(3000);

        // 3. Get results
        // Need to loop until we get an actual result
        int count = 0;
        do {
            count += 1;
            result = hylandKEService.getJobIdResult(null, jobId);
            if (!result.callResponseOK()) {
                java.lang.Thread.sleep(3000);
            }
        } while (!result.callResponseOK() || count > 5);

        assertTrue(result.callResponseOK());

        responseJson = result.getResponseAsJSONObject();
        JSONArray results = responseJson.getJSONArray("results");
        JSONObject theResult = results.getJSONObject(0);
        JSONObject descriptionJson = theResult.getJSONObject("imageDescription");
        assertTrue(descriptionJson.getBoolean("isSuccess"));

        String description = descriptionJson.getString("result");
        // We should have at least "Mickey"
        assertTrue(description.toLowerCase().indexOf("mickey") > -1);

    }

    @Test
    public void shouldGetImageDescription() throws Exception {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasEnrichmentClientInfo());

        File f = FileUtils.getResourceFileFromContext(TEST_IMAGE_PATH);
        ServiceCallResult result = hylandKEService.enrich(null, f, TEST_IMAGE_MIMETYPE, List.of("image-description"), null,
                null, null);
        assertNotNull(result);

        // Expecting HTTP OK
        assertTrue(result.callResponseOK());

        JSONObject responseJson = result.getResponseAsJSONObject();
        assertNotNull(responseJson);
        String status = responseJson.getString("status");
        assertEquals("SUCCESS", status);

        JSONArray results = responseJson.getJSONArray("results");
        JSONObject theResult = results.getJSONObject(0);
        JSONObject descriptionJson = theResult.getJSONObject("imageDescription");
        assertTrue(descriptionJson.getBoolean("isSuccess"));

        String description = descriptionJson.getString("result");
        // We should have at least "Mickey"
        assertTrue(description.toLowerCase().indexOf("mickey") > -1);
    }

    @Test
    public void shouldGetImageEmbeddings() throws Exception {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasEnrichmentClientInfo());

        File f = FileUtils.getResourceFileFromContext(TEST_IMAGE_PATH);
        ServiceCallResult result = hylandKEService.enrich(null, f, TEST_IMAGE_MIMETYPE, List.of("image-embeddings"), null,
                null, null);
        assertNotNull(result);

        // Expecting HTTP OK
        assertTrue(result.callResponseOK());

        JSONObject responseJson = result.getResponseAsJSONObject();
        String status = responseJson.getString("status");
        assertEquals("SUCCESS", status);

        JSONArray results = responseJson.getJSONArray("results");
        JSONObject theResult = results.getJSONObject(0);
        JSONObject embeddingsJson = theResult.getJSONObject("imageEmbeddings");
        assertTrue(embeddingsJson.getBoolean("isSuccess"));

        JSONArray embeddings = embeddingsJson.getJSONArray("result");
        assertTrue(embeddings.length() == 1024);
    }

    @Test
    public void shouldGetImageClassification() throws Exception {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasEnrichmentClientInfo());

        File f = FileUtils.getResourceFileFromContext(TEST_IMAGE_PATH);
        ServiceCallResult result = hylandKEService.enrich(null, f, TEST_IMAGE_MIMETYPE, List.of("image-classification"),
                List.of("Disney", "DC Comics", "Marvel"), null, null);
        assertNotNull(result);

        // Expecting HTTP OK
        assertTrue(result.callResponseOK());

        JSONObject responseJson = result.getResponseAsJSONObject();
        String status = responseJson.getString("status");
        assertEquals("SUCCESS", status);

        JSONArray results = responseJson.getJSONArray("results");
        JSONObject theResult = results.getJSONObject(0);
        JSONObject classificationJson = theResult.getJSONObject("imageClassification");
        assertTrue(classificationJson.getBoolean("isSuccess"));

        String classification = classificationJson.getString("result");
        // So far the service returns the value lowercase anyway (which is a problem if the list of values are from a
        // vocabulary)
        assertEquals("disney", classification.toLowerCase());
    }

    @Test
    public void shouldGetSeveralEnrichmentsOnImage() throws Exception {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasEnrichmentClientInfo());

        File f = FileUtils.getResourceFileFromContext(TEST_IMAGE_PATH);
        ServiceCallResult result = hylandKEService.enrich(null, f, TEST_IMAGE_MIMETYPE,
                List.of("image-description", "image-embeddings", "image-classification"),
                List.of("Disney", "DC Comics", "Marvel"), null, null);
        assertNotNull(result);

        // Expecting HTTP OK
        assertTrue(result.callResponseOK());

        JSONObject responseJson = result.getResponseAsJSONObject();
        String status = responseJson.getString("status");
        assertEquals("SUCCESS", status);

        JSONArray results = responseJson.getJSONArray("results");
        JSONObject theResult = results.getJSONObject(0);

        // ==========> Description
        JSONObject descriptionJson = theResult.getJSONObject("imageDescription");
        assertTrue(descriptionJson.getBoolean("isSuccess"));

        String description = descriptionJson.getString("result");
        // We should have at least "Mickey"
        assertTrue(description.toLowerCase().indexOf("mickey") > -1);

        // ==========> Embeddings
        JSONObject embeddingsJson = theResult.getJSONObject("imageEmbeddings");
        assertTrue(embeddingsJson.getBoolean("isSuccess"));

        JSONArray embeddings = embeddingsJson.getJSONArray("result");
        assertTrue(embeddings.length() == 1024);

        // ==========> Classification
        JSONObject classificationJson = theResult.getJSONObject("imageClassification");
        assertTrue(classificationJson.getBoolean("isSuccess"));

        String classification = classificationJson.getString("result");
        // So far the service returns the value lowercase anyway (which is a problem if the list of values are from a
        // vocabulary)
        assertEquals("disney", classification.toLowerCase());
    }

    @Test
    public void shouldGetImageMetadata() throws Exception {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasEnrichmentClientInfo());

        String similarMetadata = """
                [{
                   "dc:source": "TheSource",
                   "dc:format": "custom",
                   "dc:rights": "RESTRICTED",
                 }, {
                   "dc:source": "TheSource",
                   "dc:format": "custom|jpeg",
                   "dc:rights": "RESTRICTED",
                 },{
                   "dc:source": "Uknown",
                   "dc:format": "custom",
                   "dc:rights": "PUBLIC",
                 },{
                   "dc:source": "TheSource",
                   "dc:format": "custom",
                   "dc:rights": "RESTRICTED",
                 }]
                               """;

        File f = FileUtils.getResourceFileFromContext(TEST_IMAGE_PATH);

        JSONObject responseJson;
        String status;
        int tryCount = 0;
        // In first beat version of the service, it may fail then work fine (most of the
        // time it works fine), so let's try it 2-3 times? (except if response is not OK 200)
        do {

            ServiceCallResult result = hylandKEService.enrich(null, f, TEST_IMAGE_MIMETYPE,
                    List.of("image-metadata-generation"), null, similarMetadata, null);
            assertNotNull(result);

            // Expecting HTTP OK
            assertTrue(result.callResponseOK());

            responseJson = result.getResponseAsJSONObject();
            status = responseJson.getString("status");

            tryCount += 1;
            if (tryCount > 1) {
                System.out.println("shouldGetImageMetadata: Servcice returned " + status + "n trying again.");
            }

        } while (tryCount < 4 || !"SUCCESS".equals(status));
        // Fail after the 2-3 attempts
        assertEquals("SUCCESS", status);

        JSONArray results = responseJson.getJSONArray("results");
        JSONObject theResult = results.getJSONObject(0);
        assertNotNull(theResult);

        JSONObject metadata = theResult.getJSONObject("imageMetadata");
        assertNotNull(metadata);
        JSONObject metadataResult = metadata.getJSONObject("result");
        assertNotNull(metadataResult);

        // Given the fake similar metadata, it should find at least TheSource and "custom"
        String value = metadataResult.getString("dc:source");
        assertEquals("TheSource", value);

        value = metadataResult.getString("dc:format");
        assertEquals("custom", value);

    }

    /**
     * Shared utility.
     * 
     * @param array
     * @param key
     * @param searchStr
     * @return
     * @since 2023
     */
    public static boolean hasValueInJSONArray(JSONArray array, String key, String searchStr) {

        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            String value = obj.getString(key);
            if (StringUtils.equals(searchStr, value)) {
                return true;
            }
        }

        return false;
    }

    @Test
    public void shouldGetDescriptionOnSeveralImages() throws Exception {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasEnrichmentClientInfo());

        File f1 = FileUtils.getResourceFileFromContext(TEST_IMAGE_PATH);
        File f2 = FileUtils.getResourceFileFromContext(TEST_OTHER_IMAGE_PATH);

        @SuppressWarnings("rawtypes")
        List<ContentToProcess> content = List.of(new ContentToProcess<File>("12345", f1),
                new ContentToProcess<File>("67890", f2));

        ServiceCallResult result = hylandKEService.enrich(null, content, List.of("image-description"), null, null, null);
        assertNotNull(result);

        // Expecting HTTP OK
        assertTrue(result.callResponseOK());

        JSONArray mapping = result.getObjectKeysMapping();
        assertNotNull(mapping);
        assertEquals(2, mapping.length());
        // And we have our IDs
        assertTrue(hasValueInJSONArray(mapping, "sourceId", "12345"));
        assertTrue(hasValueInJSONArray(mapping, "sourceId", "67890"));

        JSONObject responseJson = result.getResponseAsJSONObject();
        String status = responseJson.getString("status");
        assertEquals("SUCCESS", status);

        JSONArray results = responseJson.getJSONArray("results");
        assertTrue(results.length() == 2);

        // Check we have a description with the correct object Mapping
        results.forEach(oneResult -> {
            JSONObject resultObj = (JSONObject) oneResult;

            JSONObject descriptionObj = resultObj.getJSONObject("imageDescription");
            assertNotNull(descriptionObj);

            String objectKey = resultObj.getString("objectKey");
            // Must exists in the returned mapping
            assertTrue(hasValueInJSONArray(mapping, "objectKey", objectKey));
        });
    }

    @Test
    public void shouldGetDataCuration() throws Exception {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasDataCurationClientInfo());

        File f = FileUtils.getResourceFileFromContext(TEST_CONTRACT_PATH);

        // No embeddings
        // schema MDATS - FULL - PIPELINE. See
        // https://hyland.github.io/DocumentFilters-Docs/latest/getting_started_with_document_filters/about_json_output.html#json_output_schema
        String options = "{\"normalization\": {\"quotations\": true},\"chunking\": true,\"embedding\": false, \"json_schema\": \"MDAST\"}";
        ServiceCallResult result = hylandKEService.curate(null, f, options);
        assertNotNull(result);

        // File file = new File("/Users/ME/Desktop/output-MDAST.json");
        // org.apache.commons.io.FileUtils.writeStringToFile(file, result, "UTF-8");

        // options = "{\"normalization\": {\"quotations\": true},\"chunking\": true,\"embedding\": false,
        // \"json_schema\": \"FULL\"}";
        // result = hylandKEService.curate(f, options);
        // assertNotNull(result);
        // file = new File("/Users/FileUtils/Desktop/output-FULL.json");
        // org.apache.commons.io.FileUtils.writeStringToFile(file, result, "UTF-8");

        // options = "{\"normalization\": {\"quotations\": true},\"chunking\": true,\"embedding\": false,
        // \"json_schema\": \"PIPELINE\"}";
        // result = hylandKEService.curate(f, options);
        // assertNotNull(result);
        // file = new File("/Users/FileUtils/Desktop/output-PIPELINE.json");
        // org.apache.commons.io.FileUtils.writeStringToFile(file, result, "UTF-8");

        // Expecting HTTP OK
        assertTrue(result.callResponseOK());

        JSONObject responseJson = result.getResponseAsJSONObject();
        assertNotNull(responseJson);
    }

    protected JSONObject getTextSummaryObject(ServiceCallResult result) {

        JSONObject responseJson = result.getResponseAsJSONObject();

        JSONArray results = responseJson.getJSONArray("results");
        JSONObject theResult = results.getJSONObject(0);
        JSONObject summaryObj = theResult.getJSONObject("textSummary");

        return summaryObj;
    }

    @Test
    public void shouldSummarizeText() throws Exception {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasEnrichmentClientInfo());

        File f = FileUtils.getResourceFileFromContext(TEST_CONTRACT_PATH);

        ServiceCallResult result = hylandKEService.enrich(null, f, TEST_CONTRACT_MIMETYPE, List.of("text-summarization"),
                null, null, null);
        assertNotNull(result);

        // Expecting HTTP OK
        assertTrue(result.callResponseOK());

        JSONObject summaryObj = getTextSummaryObject(result);
        assertTrue(summaryObj.getBoolean("isSuccess"));

        String summary = summaryObj.getString("result");
        assertTrue(StringUtils.isNotBlank(summary));

    }

    @Test
    public void shouldUseMaxWordCount() throws Exception {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasEnrichmentClientInfo());

        File f = FileUtils.getResourceFileFromContext(TEST_CONTRACT_PATH);

        ServiceCallResult result = hylandKEService.enrich(null, f, TEST_CONTRACT_MIMETYPE, List.of("text-summarization"),
                null, null, null);
        assertNotNull(result);

        // Here we skip result.callResponseOK() etc.

        JSONObject summaryObj = getTextSummaryObject(result);
        String summary1 = summaryObj.getString("result");

        // Now, same call, few words
        result = hylandKEService.enrich(null, f, TEST_CONTRACT_MIMETYPE, List.of("text-summarization"), null, null,
                "{\"maxWordCount\": 50}");
        assertNotNull(result);

        summaryObj = getTextSummaryObject(result);
        String summary2 = summaryObj.getString("result");

        assertTrue(summary1.length() > summary2.length());

    }

}
