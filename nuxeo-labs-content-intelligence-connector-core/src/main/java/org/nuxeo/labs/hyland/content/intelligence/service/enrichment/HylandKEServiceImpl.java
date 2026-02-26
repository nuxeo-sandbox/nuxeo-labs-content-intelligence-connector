/*
i * (C) Copyright 2025 Hyland (http://hyland.com/) and others.
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
package org.nuxeo.labs.hyland.content.intelligence.service.enrichment;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.labs.hyland.content.intelligence.authentication.AuthenticationToken;
import org.nuxeo.labs.hyland.content.intelligence.authentication.AuthenticationTokenEnrichment;
import org.nuxeo.labs.hyland.content.intelligence.ContentToProcess;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCall;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.labs.hyland.content.intelligence.service.AbstractCICServiceComponent;
import org.nuxeo.labs.hyland.content.intelligence.service.CICServiceConstants;
import org.nuxeo.labs.hyland.content.intelligence.service.ServicesUtils;
import org.nuxeo.runtime.model.ComponentContext;

public class HylandKEServiceImpl extends AbstractCICServiceComponent<KEDescriptor> implements HylandKEService {

    private static final Logger log = LogManager.getLogger(HylandKEServiceImpl.class);

    public static final String KE_USE_V2_PARAM = "nuxeo.hyland.cic.enrichment.v2";

    public static final String KE_INSTRUCTIONS_OBJ_IN_EXTRA_PAYLOAD = "instructions";

    public static final String KE_MAX_WORD_COUNT_PROPERTY = "maxWordCount";

    public static final String ENRICHMENT_CLIENT_ID_PARAM = "nuxeo.hyland.cic.enrichment.clientId";

    public static final String ENRICHMENT_CLIENT_SECRET_PARAM = "nuxeo.hyland.cic.enrichment.clientSecret";

    public static final String CONTEXT_ENRICHMENT_BASE_URL_PARAM = "nuxeo.hyland.cic.contextEnrichment.baseUrl";

    public static final String PULL_RESULTS_MAX_TRIES_PARAM = "nuxeo.hyland.cic.pullResultsMaxTries";

    public static final int PULL_RESULTS_MAX_TRIES_DEFAULT = 25;

    public static final String PULL_RESULTS_SLEEP_INTERVAL_PARAM = "nuxeo.hyland.cic.pullResultsSleepInterval";

    public static final int PULL_RESULTS_SLEEP_INTERVAL_DEFAULT = 5000;

    protected static Map<String, AuthenticationToken> enrichmentAuthTokens = null;

    protected static int pullResultsMaxTries;

    protected static int pullResultsSleepIntervalMS;

    public static final String CUSTOM_ID_PREFIX = "CUSTOM_ID-";

    protected static ServiceCall serviceCall = new ServiceCall();

    protected static boolean useKEV2 = false;

    // ====================> Extensions points
    protected static final String EXT_POINT_KE = "knowledgeEnrichment";

    // ======================================================================
    // ======================================================================
    // Utils and Setup
    // ======================================================================
    // ======================================================================

    public HylandKEServiceImpl() {
        initialize();
    }

    public int getPullResultsMaxTries() {
        return pullResultsMaxTries;
    }

    public int getPullResultsSleepIntervalMS() {
        return pullResultsSleepIntervalMS;
    }

    protected String getCustomUUID() {
        String uuid = UUID.randomUUID().toString();
        return CUSTOM_ID_PREFIX + uuid.substring(CUSTOM_ID_PREFIX.length());
    }

    protected void initialize() {

        pullResultsMaxTries = ServicesUtils.configParamToInt(PULL_RESULTS_MAX_TRIES_PARAM,
                PULL_RESULTS_MAX_TRIES_DEFAULT);

        pullResultsSleepIntervalMS = ServicesUtils.configParamToInt(PULL_RESULTS_SLEEP_INTERVAL_PARAM,
                PULL_RESULTS_SLEEP_INTERVAL_DEFAULT);

        useKEV2 = ServicesUtils.configParamToBoolean(KE_USE_V2_PARAM, false);

        logConfigurationInfo();
    }

    protected void logConfigurationInfo() {

        // We don't want a WARN, this is an INFO
        String msg = "Knowledge Enrichment startup configuration:\n  pullResultsMaxTries=" + pullResultsMaxTries;
        msg += "\n  pullResultsSleepIntervalMS=" + pullResultsSleepIntervalMS;
        msg += "\n  useKEV2=" + useKEV2;
        ServicesUtils.forceLogInfo(this.getClass(), msg);
    }

    protected String getKEToken(String configName) {
        return super.getToken(enrichmentAuthTokens, configName);
    }

    @Override
    protected String getDescriptorExtensionPoint() {
        return EXT_POINT_KE;
    }

    @Override
    protected String getServiceLabel() {
        return "Knowledge Enrichment";
    }

    // ======================================================================
    // ======================================================================
    // Service Implementation
    // ======================================================================
    // ======================================================================
    @Override
    public void setUseKEV2(boolean value) {
        useKEV2 = value;

        logConfigurationInfo();
    }

    @Override
    public boolean getUseKEV2() {
        return useKEV2;
    }

    @Override
    public void setPullResultsSettings(int maxTries, int sleepIntervalMS) {

        switch (maxTries) {
        case 0:
            // Revert to config or default
            pullResultsMaxTries = ServicesUtils.configParamToInt(PULL_RESULTS_MAX_TRIES_PARAM,
                    PULL_RESULTS_MAX_TRIES_DEFAULT);
            break;

        case -1:
            // No change
            break;

        default:
            pullResultsMaxTries = maxTries;
            break;
        }

        switch (sleepIntervalMS) {
        case 0:
            pullResultsSleepIntervalMS = ServicesUtils.configParamToInt(PULL_RESULTS_SLEEP_INTERVAL_PARAM,
                    PULL_RESULTS_SLEEP_INTERVAL_DEFAULT);
            break;

        case -1:
            // No change
            break;

        default:
            pullResultsSleepIntervalMS = sleepIntervalMS;
            break;
        }

        logConfigurationInfo();
    }

    @Override
    public ServiceCallResult getJobIdResult(String configName, String jobId) {

        ServiceCallResult result = null;

        result = invokeEnrichment(configName, "GET", "/content/process/" + jobId + "/results", null);

        return result;
    }

    @Override
    public ServiceCallResult sendForEnrichment(String configName, Blob blob, String sourceId, List<String> actions,
            List<String> classes, String similarMetadataJsonArrayStr, String extraJsonPayloadStr) throws IOException {

        if (StringUtils.isBlank(sourceId)) {
            sourceId = getCustomUUID();
        }

        @SuppressWarnings("rawtypes")
        List<ContentToProcess> contentToProcess = new ArrayList<ContentToProcess>();
        ContentToProcess<Blob> oneContent = new ContentToProcess<Blob>(sourceId, blob);
        contentToProcess.add(oneContent);

        ServiceCallResult result = sendForEnrichment(configName, contentToProcess, actions, classes,
                similarMetadataJsonArrayStr, extraJsonPayloadStr);

        return result;

    }

    @Override
    public ServiceCallResult sendForEnrichment(String configName, File file, String sourceId, String mimeType,
            List<String> actions, List<String> classes, String similarMetadataJsonArrayStr, String extraJsonPayloadStr)
            throws IOException {

        if (StringUtils.isBlank(sourceId)) {
            sourceId = getCustomUUID();
        }

        @SuppressWarnings("rawtypes")
        List<ContentToProcess> contentToProcess = new ArrayList<ContentToProcess>();
        ContentToProcess<File> oneContent = new ContentToProcess<File>(sourceId, file);
        contentToProcess.add(oneContent);

        ServiceCallResult result = sendForEnrichment(configName, contentToProcess, actions, classes,
                similarMetadataJsonArrayStr, extraJsonPayloadStr);

        return result;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public ServiceCallResult sendForEnrichment(String configName, List<ContentToProcess> contentObjects,
            List<String> actions, List<String> classes, String similarMetadataJsonArrayStr, String extraJsonPayloadStr)
            throws IOException {

        ServiceCallResult result = null;
        JSONObject serviceResponse;

        // (1. Token will be handled at first call)

        // 2. Get presigned URL for every file
        String errMsg;
        for (ContentToProcess content : contentObjects) {

            result = invokeEnrichment(configName, "GET",
                    "/files/upload/presigned-url?contentType=" + content.getMimeType().replace("/", "%2F"), null);
            if (result.callFailed()) {
                // return result;

                errMsg = "Failed getting a presigned URL for content ID <" + content.getSourceId() + ">, File name <"
                        + content.getFile().getName() + ">.";
                log.error(errMsg);
                content.setErrorMessage(errMsg);
                content.setProcessingSuccess(false);
                continue;
            }

            serviceResponse = result.getResponseAsJSONObject();
            String presignedUrl = serviceResponse.getString("presignedUrl");
            String objectKey = serviceResponse.getString("objectKey");
            content.setObjectKey(objectKey);

            // 3. Upload file to this URL
            result = serviceCall.uploadFileWithPut(content.getFile(), presignedUrl, content.getMimeType());
            if (result.callFailed()) {
                errMsg = "Failed uploading content ID <" + content.getSourceId() + ">, File name <\"\n"
                        + content.getFile().getName() + ">.";
                log.error(errMsg);
                content.setErrorMessage(errMsg);
                content.setProcessingSuccess(false);
                continue;
            }

            content.setProcessingSuccess(true);

        }

        // We need to cleanup and close() any potential CloseableFile fetched during the loop
        for (ContentToProcess content : contentObjects) {
            content.close();
        }

        // 4. Get available actions
        // (Not needed here)

        // 5. Process
        List<String> objectKeys = contentObjects.stream()
                                                .filter(ContentToProcess::isProcessingSuccess)
                                                .map(ContentToProcess::getObjectKey)
                                                .collect(Collectors.toList());

        JSONObject payload = buildProcessActionPayload(objectKeys, actions, classes, similarMetadataJsonArrayStr,
                extraJsonPayloadStr);
        result = invokeEnrichment(configName, "POST", "/content/process", payload.toString());

        return result;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public ServiceCallResult enrich(String configName, List<ContentToProcess> contentObjects, List<String> actions,
            List<String> classes, String similarMetadataJsonArrayStr, String extraJsonPayloadStr) throws IOException {

        ServiceCallResult result = null;
        JSONObject serviceResponse;

        if (log.isInfoEnabled()) {
            StringBuilder sb = new StringBuilder("HylandKEServiceImpl#enrich:");
            sb.append("\n  configName: ").append(StringUtils.isBlank(configName) ? "default" : configName);
            // We can assume contentObjects has at least one file.
            sb.append("\n  contentObjects: ")
              .append(contentObjects.stream()
                                    .map(c -> c != null && c.getFile() != null ? c.getFile().getName() : "null")
                                    .toList());
            sb.append("\n  actions: ").append(actions);
            sb.append("\n  classes: ").append(classes);
            sb.append("\n  similarMetadataJsonArrayStr: ").append(similarMetadataJsonArrayStr);
            sb.append("\n  extraJsonPayloadStr: ").append(extraJsonPayloadStr);

            log.info(sb.toString());
        }

        result = sendForEnrichment(configName, contentObjects, actions, classes, similarMetadataJsonArrayStr,
                extraJsonPayloadStr);
        if (result.callFailed()) {
            return result;
        }
        serviceResponse = result.getResponseAsJSONObject();
        String resultId = serviceResponse.getString("processingId");

        result = pullEnrichmentResults(configName, resultId);

        // Add the info so that caller can map objectKey and their blob/file
        if (result.callWasSuccesful()) {
            JSONObject response = result.getResponseAsJSONObject();

            if (!response.has("results")) {
                // Likely we stopped pulling before getting a result (maxtries reached, typically)
                String msg = "No \"results\" key in the response. Did we get a final result? Original message: "
                        + result.getResponseMessage();
                result = new ServiceCallResult(response.toString(), result.getResponseCode(), msg);
            } else {
                JSONArray results = response.getJSONArray("results");
                JSONArray mapping = new JSONArray();
                results.forEach(oneResult -> {
                    String objectKey = ((JSONObject) oneResult).getString("objectKey");
                    ContentToProcess found = contentObjects.stream()
                                                           .filter(content -> objectKey.equals(content.getObjectKey()))
                                                           .findFirst()
                                                           .orElse(null);
                    if (found != null) {
                        JSONObject obj = new JSONObject();
                        obj.put("sourceId", found.getSourceId());
                        obj.put("objectKey", objectKey);
                        mapping.put(obj);
                    }
                });

                result.setObjectKeysMapping(mapping);
            }
        }

        return result;
    }

    @Override
    public ServiceCallResult enrich(String configName, Blob blob, List<String> actions, List<String> classes,
            String similarMetadataJsonArrayStr, String extraJsonPayloadStr) throws IOException {

        String sourceId = getCustomUUID();

        @SuppressWarnings("rawtypes")
        List<ContentToProcess> contentToProcess = new ArrayList<ContentToProcess>();
        ContentToProcess<Blob> oneContent = new ContentToProcess<Blob>(sourceId, blob);
        contentToProcess.add(oneContent);

        ServiceCallResult result = enrich(configName, contentToProcess, actions, classes, similarMetadataJsonArrayStr,
                extraJsonPayloadStr);

        return result;
    }

    protected JSONObject buildProcessActionPayload(List<String> objectKeys, List<String> actions, List<String> classes,
            String similarMetadataJsonArrayStr, String extraJsonPayloadStr) {

        JSONObject payload = new JSONObject();

        JSONObject extraJsonPayload = new JSONObject();
        if (StringUtils.isNotBlank(extraJsonPayloadStr)) {
            extraJsonPayload = new JSONObject(extraJsonPayloadStr);
        }

        Integer maxWordCount = extraJsonPayload.optIntegerObject("maxWordCount", null);

        if (useKEV2) {
            payload.put("version", "context.api/v2");
            JSONArray objKeysV2 = new JSONArray();
            for (String oneKey : objectKeys) {
                JSONObject oneKeyObj = new JSONObject();
                oneKeyObj.put("path", oneKey);
                objKeysV2.put(oneKeyObj);
            }
            payload.put("objectKeys", objKeysV2);

            JSONObject instructions = extraJsonPayload.optJSONObject(KE_INSTRUCTIONS_OBJ_IN_EXTRA_PAYLOAD,
                    new JSONObject());

            JSONObject actionsObj = new JSONObject();
            for (String oneAction : actions) {
                JSONObject oneActionObj = new JSONObject();
                switch (oneAction) {
                case "textClassification":
                case "imageClassification":
                    oneActionObj.put("Classes", new JSONArray(classes));
                    break;

                case "imageMetadataGeneration":
                case "textMetadataGeneration":
                    oneActionObj.put("kSimilarMetadata", new JSONArray(similarMetadataJsonArrayStr));
                    break;

                case "imageDescription":
                case "textSummarization":
                    if (maxWordCount != null) {
                        oneActionObj.put("maxWordCount", maxWordCount);
                    }
                    break;

                default:
                    break;
                }

                JSONObject oneActionInstructions = instructions.optJSONObject(oneAction);
                if (oneActionInstructions != null) {
                    oneActionObj.put("instructions", oneActionInstructions);
                }

                actionsObj.put(oneAction, oneActionObj);
            }

            payload.put("actions", actionsObj);

        } else {
            payload.put("objectKeys", new JSONArray(objectKeys));
            payload.put("actions", new JSONArray(actions));
            if (similarMetadataJsonArrayStr == null) {
                payload.put("kSimilarMetadata", new JSONArray());
            } else {
                payload.put("kSimilarMetadata", new JSONArray(similarMetadataJsonArrayStr));
            }
            if (classes == null) {
                payload.put("classes", new JSONArray());
            } else {
                payload.put("classes", new JSONArray(classes));
            }
        }

        if (!extraJsonPayload.isEmpty()) {
            for (String key : JSONObject.getNames(extraJsonPayload)) {
                if (!useKEV2 || !KE_INSTRUCTIONS_OBJ_IN_EXTRA_PAYLOAD.equals(key)) {
                    payload.put(key, extraJsonPayload.get(key));
                }
            }
        }

        return payload;
    }

    @Override
    public ServiceCallResult enrich(String configName, File file, String mimeType, List<String> actions,
            List<String> classes, String similarMetadataJsonArrayStr, String extraJsonPayloadStr) throws IOException {

        String sourceId = getCustomUUID();

        @SuppressWarnings("rawtypes")
        List<ContentToProcess> contentToProcess = new ArrayList<ContentToProcess>();
        ContentToProcess<File> oneContent = new ContentToProcess<File>(sourceId, file);
        contentToProcess.add(oneContent);

        ServiceCallResult result = enrich(configName, contentToProcess, actions, classes, similarMetadataJsonArrayStr,
                extraJsonPayloadStr);

        return result;
    }

    protected ServiceCallResult pullEnrichmentResults(String configName, String resultId) {

        ServiceCallResult result;
        int count = 1;

        log.info("pullEnrichmentResults for Job ID '" + resultId + "'.");

        do {
            if (count > 1) {
                try {
                    Thread.sleep(pullResultsSleepIntervalMS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (count == pullResultsMaxTries) {
                log.warn("Pulling Enrichment results is taking time. This is the last try,  " + count + "/"
                        + pullResultsMaxTries);
            } else if (count == 5 || (count > 5 && (count - 5) % 2 == 0)) {
                log.warn("Pulling Enrichment results is taking time. This is the call #" + count + " (max calls: "
                        + pullResultsMaxTries + ")");
                if (count == 5) {
                    KEDescriptor config = getKEDescriptor(configName);
                    log.warn("(Pulling job ID '" + resultId + "', configuration '" + config.getName() + "')");
                }
            }

            result = getJobIdResult(configName, resultId);
            count += 1;

            // We must get an OK. A 202 "Accepted" for example does not have the full response.
        } while (!result.callResponseOK() && count <= pullResultsMaxTries);

        return result;
    }

    @Override
    public ServiceCallResult invokeEnrichment(String configName, String httpMethod, String endpoint,
            String jsonPayload) {

        ServiceCallResult result = null;

        // Get auth token
        String bearer = getKEToken(null);// enrichmentAuthToken.getToken();
        if (StringUtils.isBlank(bearer)) {
            throw new NuxeoException("No authentication info for calling the Enrichment service.");
        }

        // URL/endpoint
        KEDescriptor config = getKEDescriptor(configName);
        String targetUrl = config.getBaseUrl();
        if (!endpoint.startsWith("/")) {
            targetUrl += "/";
        }
        targetUrl += endpoint;

        // Headers
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Accept", "*/*");
        headers.put("Authorization", "Bearer " + bearer);
        if (endpoint.startsWith("/content/process")) {
            headers.put("Content-Type", "application/json");
        }

        // Run
        httpMethod = httpMethod.toUpperCase();
        switch (httpMethod) {
        case "GET":
            result = serviceCall.get(targetUrl, headers);
            break;

        case "POST":
            result = serviceCall.post(targetUrl, headers, jsonPayload);
            break;

        case "PUT":
            result = serviceCall.put(targetUrl, headers, jsonPayload);
            break;

        default:
            throw new NuxeoException("Only GET, POST and PUT are supported.");
        }

        return result;

    }

    // ======================================================================
    // ======================================================================
    // Service Configuration
    // ======================================================================
    // ======================================================================
    @Override
    public List<String> getContribNames() {
        return super.getContribNames();
    }

    @Override
    public KEDescriptor getKEDescriptor(String configName) {
        return super.getDescriptor(configName);
    }

    /**
     * Start the component. This method is called after all the components were resolved and activated
     *
     * @param context the component context. Use it to get the current bundle context
     */
    @Override
    public void start(ComponentContext context) {

        enrichmentAuthTokens = initAuthTokens(
                desc -> new AuthenticationTokenEnrichment(
                        desc.getAuthenticationBaseUrl() + CICServiceConstants.AUTH_ENDPOINT,
                desc.getAuthenticationTokenParams()));
    }

    /**
     * Stop the component.
     *
     * @param context the component context. Use it to get the current bundle context
     * @throws InterruptedException
     */
    @Override
    public void stop(ComponentContext context) throws InterruptedException {

        // Nothing for now

    }

}
