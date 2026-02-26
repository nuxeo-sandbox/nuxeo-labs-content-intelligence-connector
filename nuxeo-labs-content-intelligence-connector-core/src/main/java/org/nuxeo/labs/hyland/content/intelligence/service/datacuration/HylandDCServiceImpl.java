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
package org.nuxeo.labs.hyland.content.intelligence.service.datacuration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CloseableFile;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.labs.hyland.content.intelligence.AuthenticationToken;
import org.nuxeo.labs.hyland.content.intelligence.AuthenticationTokenEnrichment;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCall;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.labs.hyland.content.intelligence.service.ServicesUtils;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.model.Extension;

/**
 * 
 * @since 2025.15/2023.18
 */
public class HylandDCServiceImpl extends DefaultComponent implements HylandDCService {

    private static final Logger log = LogManager.getLogger(HylandDCServiceImpl.class);

    public static final String DATA_CURATION_CLIENT_ID_PARAM = "nuxeo.hyland.cic.datacuration.clientId";

    public static final String DATA_CURATION_CLIENT_SECRET_PARAM = "nuxeo.hyland.cic.datacuration.clientSecret";

    // Will add "/connect/token" to this baseUrl.
    public static final String AUTH_BASE_URL_PARAM = "nuxeo.hyland.cic.auth.baseUrl";

    public static final String AUTH_ENDPOINT = "/connect/token";

    public static final String DATA_CURATION_BASE_URL_PARAM = "nuxeo.hyland.cic.dataCuration.baseUrl";

    public static final String PULL_RESULTS_MAX_TRIES_PARAM = "nuxeo.hyland.cic.datacuration.pullResultsMaxTries";

    public static final int PULL_RESULTS_MAX_TRIES_DEFAULT = 25;

    public static final String PULL_RESULTS_SLEEP_INTERVAL_PARAM = "nuxeo.hyland.cic.datacuration.pullResultsSleepInterval";

    public static final int PULL_RESULTS_SLEEP_INTERVAL_DEFAULT = 5000;

    public static final String DATA_CURATION_PRESIGN_DEFAULT_OPTIONS = "{\"normalization\": {\"quotations\": true},\"chunking\": true,\"embedding\": true,\"json_schema\": \"PIPELINE\"}";

    protected static Map<String, AuthenticationToken> dataCurationAuthTokens = null;

    protected static int pullResultsMaxTries;

    protected static int pullResultsSleepIntervalMS;

    protected static ServiceCall serviceCall = new ServiceCall();

    // ====================> Extensions points
    protected static final String EXT_POINT_DC = "dataCuration";

    protected Map<String, DCDescriptor> dcContribs = new HashMap<String, DCDescriptor>();

    public static final String CONFIG_DEFAULT = "default";

    // ======================================================================
    // ======================================================================
    // Utils and Setup
    // ======================================================================
    // ======================================================================

    public HylandDCServiceImpl() {
        initialize();
    }

    public int getPullResultsMaxTries() {
        return pullResultsMaxTries;
    }

    public int getPullResultsSleepIntervalMS() {
        return pullResultsSleepIntervalMS;
    }

    protected void initialize() {

        pullResultsMaxTries = ServicesUtils.configParamToInt(PULL_RESULTS_MAX_TRIES_PARAM,
                PULL_RESULTS_MAX_TRIES_DEFAULT);

        pullResultsSleepIntervalMS = ServicesUtils.configParamToInt(PULL_RESULTS_SLEEP_INTERVAL_PARAM,
                PULL_RESULTS_SLEEP_INTERVAL_DEFAULT);

        logConfigurationInfo();
    }

    protected void logConfigurationInfo() {

        // We don't want a WARN, this is an INFO
        String msg = "Data Curation startup configuration:\n  pullResultsMaxTries=" + pullResultsMaxTries;
        msg += "\n  pullResultsSleepIntervalMS=" + pullResultsSleepIntervalMS;
        ServicesUtils.forceLogInfo(this.getClass(), msg);
    }

    protected String getDCToken(String configName) {

        if (StringUtils.isBlank(configName)) {
            configName = CONFIG_DEFAULT;
        }

        AuthenticationToken token = dataCurationAuthTokens.get(configName);

        return token.getToken();
    }

    // ======================================================================
    // ======================================================================
    // Service Implementation
    // ======================================================================
    // ======================================================================

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
    public ServiceCallResult curate(String configName, Blob blob, String jsonOptions) throws IOException {

        try (CloseableFile closFile = blob.getCloseableFile()) {
            return curate(configName, closFile.getFile(), jsonOptions);
        }
    }

    @Override
    public ServiceCallResult curate(String configName, File file, String jsonOptions) throws IOException {

        ServiceCallResult result;
        JSONObject jsonPresign;
        String jobId = null;
        String getUrl = null;
        String putUrl = null;

        // ====================> 1. Get auth token
        String bearer = getDCToken(configName);
        if (StringUtils.isBlank(bearer)) {
            throw new NuxeoException("No authentication info for calling the Data Curation service, for configuration '"
                    + configName + "'.");
        }

        // ====================> 2. Get presigned stuff
        DCDescriptor config = getDCDescriptor(configName);
        String targetUrl = config.getBaseUrl();
        targetUrl += "/presign";

        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Accept", "*/*");
        headers.put("Authorization", "Bearer " + bearer);
        // headers.put("Content-Type", "application/json");

        if (StringUtils.isBlank(jsonOptions)) {
            jsonOptions = DATA_CURATION_PRESIGN_DEFAULT_OPTIONS;
        }

        result = serviceCall.post(targetUrl, headers, jsonOptions);
        if (result.callFailed()) {
            return result;
        }
        jsonPresign = result.getResponseAsJSONObject();
        jobId = jsonPresign.getString("job_id");
        putUrl = jsonPresign.getString("put_url");
        getUrl = jsonPresign.getString("get_url");

        // ====================> 3. Upload with PUT
        result = serviceCall.uploadFileWithPut(file, putUrl, "application/octet-stream");
        if (result.callFailed()) {
            return result;
        }

        // ====================> 4. Pull results
        result = pullDataCurationResults(configName, jobId, getUrl);

        return result;

    }

    /*
     * Pull to dataCurationEndPoint/status/job_id until getting it "Done"
     * Once "Done", just GET at the getUrl (presigned)
     */
    protected ServiceCallResult pullDataCurationResults(String configName, String jobId, String getUrl) {

        ServiceCallResult result = null;
        int count = 1;

        if (StringUtils.isBlank(jobId) || StringUtils.isBlank(getUrl)) {
            throw new IllegalArgumentException("jobId and/or getUrl - presigned - is/are null");
        }

        DCDescriptor config = getDCDescriptor(configName);
        String targetUrl = config.getBaseUrl() + "/status/" + jobId;
        boolean gotIt = false;
        do {
            if (count > 1) {
                try {
                    Thread.sleep(pullResultsSleepIntervalMS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (count > (pullResultsMaxTries / 2)) {
                log.warn("Pulling Data Curation results is taking time. This is the call #" + count + " (max calls: "
                        + pullResultsMaxTries + ")");
            }

            String bearer = getDCToken(configName);
            if (StringUtils.isBlank(bearer)) {
                throw new NuxeoException(
                        "No authentication info for calling the Data Curation service, for configuration '" + configName
                                + "'.");
            }

            Map<String, String> headers = new HashMap<String, String>();
            headers.put("Authorization", "Bearer " + bearer);

            result = serviceCall.get(targetUrl, headers);
            if (result.callWasSuccesful()) {
                JSONObject resultJson = result.getResponseAsJSONObject();
                String responseJobId = resultJson.getString("jobId");
                if (!responseJobId.equals(jobId)) {
                    String msg = "Received OK for a different jobID. Expected jobId: " + jobId + ", received: "
                            + responseJobId;
                    log.warn(msg);
                    // Not really a HTTP status, right?
                    // But this is needed with the while(...) part.
                    result = new ServiceCallResult("{}", -2, msg);
                } else {
                    String status = resultJson.getString("status");
                    if (status.toLowerCase().equals("done")) {
                        // Just GET at the presigned URL, no headers required
                        result = serviceCall.get(getUrl, null);
                        if (result.callWasSuccesful()) {
                            gotIt = true;
                        }
                    } else {
                        log.info("Pulling Data Curation status for job " + jobId + ", status: " + status);
                    }
                }
            }

            count += 1;

        } while (!gotIt && count <= pullResultsMaxTries);

        return result;

    }

    // ======================================================================
    // ======================================================================
    // Service Configuration
    // ======================================================================
    // ======================================================================

    @Override
    public List<String> getContribNames() {

        if (dcContribs == null) {
            dcContribs = new HashMap<String, DCDescriptor>();
        }

        return new ArrayList<>(dcContribs.keySet());

    }

    @Override
    public DCDescriptor getDCDescriptor(String configName) {

        if (StringUtils.isBlank(configName)) {
            configName = CONFIG_DEFAULT;
        }

        return dcContribs.get(configName);
    }

    /**
     * Component activated notification.
     * Called when the component is activated. All component dependencies are resolved at that moment.
     * Use this method to initialize the component.
     *
     * @param context the component context.
     */
    @Override
    public void activate(ComponentContext context) {
        super.activate(context);
    }

    /**
     * Component deactivated notification.
     * Called before a component is unregistered.
     * Use this method to do cleanup if any and free any resources held by the component.
     *
     * @param context the component context.
     */
    @Override
    public void deactivate(ComponentContext context) {
        super.deactivate(context);
    }

    /**
     * Registers the given extension.
     *
     * @param extension the extension to register
     */
    @Override
    public void registerExtension(Extension extension) {
        super.registerExtension(extension);

        if (dcContribs == null) {
            dcContribs = new HashMap<String, DCDescriptor>();
        }

        if (EXT_POINT_DC.equals(extension.getExtensionPoint())) {
            Object[] contribs = extension.getContributions();
            if (contribs != null) {
                for (Object contrib : contribs) {
                    DCDescriptor desc = (DCDescriptor) contrib;
                    dcContribs.put(desc.getName(), desc);
                }
            }
        }
    }

    /**
     * Unregisters the given extension.
     *
     * @param extension the extension to unregister
     */
    @Override
    public void unregisterExtension(Extension extension) {
        super.unregisterExtension(extension);

        if (dcContribs == null) {
            return;
        }

        if (EXT_POINT_DC.equals(extension.getExtensionPoint())) {
            Object[] contribs = extension.getContributions();
            if (contribs != null) {
                for (Object contrib : contribs) {
                    DCDescriptor desc = (DCDescriptor) contrib;
                    dcContribs.remove(desc.getName());
                }
            }
        }
    }

    /**
     * Start the component. This method is called after all the components were resolved and activated
     *
     * @param context the component context. Use it to get the current bundle context
     */
    @Override
    public void start(ComponentContext context) {
        // OK, all extensions loaded, let's initialize the auth. tokens
        if (dcContribs == null) {
            log.error("No configuration found for Data Curation. Calls, if any, will fail.");
        } else {
            dataCurationAuthTokens = new HashMap<String, AuthenticationToken>();
            for (Map.Entry<String, DCDescriptor> entry : dcContribs.entrySet()) {
                DCDescriptor desc = entry.getValue();
                AuthenticationToken token = new AuthenticationTokenEnrichment(
                        desc.getAuthenticationBaseUrl() + AUTH_ENDPOINT, desc.getAuthenticationTokenParams());
                dataCurationAuthTokens.put(desc.getName(), token);

                desc.checkConfigAndLogErrors();
            }
        }
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
