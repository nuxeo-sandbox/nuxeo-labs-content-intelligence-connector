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
package org.nuxeo.labs.hyland.content.intelligence.service.ingest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.labs.hyland.content.intelligence.authentication.AuthenticationToken;
import org.nuxeo.labs.hyland.content.intelligence.authentication.AuthenticationTokenIngestion;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCall;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.labs.hyland.content.intelligence.service.AbstractCICServiceComponent;
import org.nuxeo.labs.hyland.content.intelligence.service.CICServiceConstants;
import org.nuxeo.labs.hyland.content.intelligence.service.ServicesUtils;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;

/**
 * @since 2025.15/2023.18
 */
public class IngestServiceImpl extends AbstractCICServiceComponent<IngestDescriptor> implements IngestService {

    private static final Logger log = LogManager.getLogger(IngestServiceImpl.class);

    public static final String INGEST_CLIENT_ID_PARAM = "nuxeo.hyland.cic.ingest.clientId";

    public static final String INGEST_CLIENT_SECRET_PARAM = "nuxeo.hyland.cic.ingest.clientSecret";

    public static final String INGEST_BASE_URL_PARAM = "nuxeo.hyland.cic.ingest.baseUrl";

    public static final String INGEST_ENVIRONMENT_PARAM = "nuxeo.hyland.cic.ingest.environment";

    public static final String INGEST_DEFAULT_SOURCEID_PARAM = "nuxeo.hyland.cic.ingest.default.sourceId";

    protected static Map<String, AuthenticationToken> ingestAuthTokens = null;

    protected static String defaultSourceId;

    protected static ServiceCall serviceCall = new ServiceCall();

    // ====================> Extensions points
    protected static final String EXT_POINT_INGEST = "ingest";

    // ====================> Endpoints
    public static final String ENDPOINT_CHECK_DIGEST = "/v1/check-digest";

    // ======================================================================
    // ======================================================================
    // Internal
    // ======================================================================
    // ======================================================================
    public IngestServiceImpl() {
        initialize();
    }

    protected void initialize() {
        defaultSourceId = Framework.getProperty(INGEST_DEFAULT_SOURCEID_PARAM);

        // We don't want a WARN, this is an INFO
        String msg = "Startup configuration:";
        msg += "\n  defaultSourceId=" + defaultSourceId;
        ServicesUtils.forceLogInfo(this.getClass(), msg);
    }

    protected String getToken(String configName) {
        return super.getToken(ingestAuthTokens, configName);
    }

    @Override
    protected String getDescriptorExtensionPoint() {
        return EXT_POINT_INGEST;
    }

    @Override
    protected String getServiceLabel() {
        return "Content Lake";
    }

    // ======================================================================
    // ======================================================================
    // Service Methods
    // ======================================================================
    // ======================================================================
    @Override
    public ServiceCallResult checkDigest(String configName, DocumentModel doc, String xpath) {

        return checkDigest(configName, doc, xpath, null);
    }

    @Override
    public ServiceCallResult checkDigest(String configName, DocumentModel doc, String xpath, String sourceId) {

        if (StringUtils.isBlank(xpath)) {
            xpath = "file:content";
        }
        Blob blob = (Blob) doc.getPropertyValue(xpath);
        if (blob == null) {
            log.error("No blob at xpath " + xpath);
            return new ServiceCallResult("{}", -1, "No blob at xpath " + xpath);
        }

        String digest = blob.getDigest();
        if (StringUtils.isBlank(digest)) {
            log.error("No digest for blob at xpath " + xpath);
            return new ServiceCallResult("{}", -1, "No digest for blob at xpath " + xpath);
        }

        return checkDigest(configName, doc.getId(), digest, sourceId);

    }

    @Override
    public ServiceCallResult checkDigest(String configName, String docId, String blobDigest, String sourceId) {

        if (StringUtils.isBlank(sourceId)) {
            sourceId = defaultSourceId;
        }
        if (StringUtils.isBlank(sourceId)) {
            log.error("No sourceId available?");
            return new ServiceCallResult("{}", -1, "No sourceId available?");
        }

        ServiceCallResult result = null;

        // Get auth token
        String bearer = getToken(configName);
        if (StringUtils.isBlank(bearer)) {
            log.error("No authentication info for calling the Content Lake service.");
            return new ServiceCallResult("{}", -1, "No authentication info for calling the Content Lake service.");
        }

        // URL/endpoint
        IngestDescriptor config = getDescriptor(configName);
        String targetUrl = config.getBaseUrl();
        if (targetUrl.endsWith("/")) {
            targetUrl = targetUrl.substring(0, targetUrl.length() - 1);
        }
        targetUrl += ENDPOINT_CHECK_DIGEST;
        // Add parameters
        targetUrl += "/" + sourceId + "/" + docId + "?digest=" + blobDigest + "&useContentLake=true";

        // Headers
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Accept", "*/*");
        headers.put("Authorization", "Bearer " + bearer);
        // More specific Content Lake
        headers.put("hxp-Environment", config.getEnvironment());

        // Call
        result = serviceCall.get(targetUrl, headers);

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
    public IngestDescriptor getDescriptor(String configName) {
        return super.getDescriptor(configName);
    }

    /**
     * Start the component. This method is called after all the components were resolved and activated
     *
     * @param context the component context. Use it to get the current bundle context
     */
    @Override
    public void start(ComponentContext context) {

        ingestAuthTokens = initAuthTokens(
                desc -> new AuthenticationTokenIngestion(
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

        // log.warn("Stop component");
    }

}
