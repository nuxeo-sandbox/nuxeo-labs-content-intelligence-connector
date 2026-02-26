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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.labs.hyland.content.intelligence.AuthenticationToken;
import org.nuxeo.labs.hyland.content.intelligence.AuthenticationTokenIngestion;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCall;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.labs.hyland.content.intelligence.service.ServicesUtils;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.HylandKEServiceImpl;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.model.Extension;

/**
 * @since 2025.15/2023.18
 */
public class IngestServiceImpl extends DefaultComponent implements IngestService {

    private static final Logger log = LogManager.getLogger(IngestServiceImpl.class);

    // Will add "/connect/token" to this endpoint.
    public static final String AUTH_BASE_URL_PARAM = HylandKEServiceImpl.AUTH_BASE_URL_PARAM;

    public static final String AUTH_ENDPOINT = HylandKEServiceImpl.AUTH_ENDPOINT;

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

    protected Map<String, IngestDescriptor> ingestContribs = new HashMap<String, IngestDescriptor>();

    public static final String CONFIG_DEFAULT = "default";

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

        if (StringUtils.isBlank(configName)) {
            configName = CONFIG_DEFAULT;
        }

        AuthenticationToken token = ingestAuthTokens.get(configName);

        return token.getToken();
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

        if (ingestContribs == null) {
            ingestContribs = new HashMap<String, IngestDescriptor>();
        }

        return new ArrayList<>(ingestContribs.keySet());
    }

    @Override
    public IngestDescriptor getDescriptor(String configName) {

        if (StringUtils.isBlank(configName)) {
            configName = CONFIG_DEFAULT;
        }

        return ingestContribs.get(configName);
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
        // log.warn("activate component");
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
        // log.warn("deactivate component");
    }

    /**
     * Registers the given extension.
     *
     * @param extension the extension to register
     */
    @Override
    public void registerExtension(Extension extension) {
        super.registerExtension(extension);

        if (ingestContribs == null) {
            ingestContribs = new HashMap<String, IngestDescriptor>();
        }

        if (EXT_POINT_INGEST.equals(extension.getExtensionPoint())) {
            Object[] contribs = extension.getContributions();
            if (contribs != null) {
                for (Object contrib : contribs) {
                    IngestDescriptor desc = (IngestDescriptor) contrib;
                    ingestContribs.put(desc.getName(), desc);
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

        if (ingestContribs == null) {
            return;
        }

        if (EXT_POINT_INGEST.equals(extension.getExtensionPoint())) {
            Object[] contribs = extension.getContributions();
            if (contribs != null) {
                for (Object contrib : contribs) {
                    IngestDescriptor desc = (IngestDescriptor) contrib;
                    ingestContribs.remove(desc.getName());
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
        // log.warn("Start component");

        // OK, all extensions loaded, let's initialize the auth. tokens
        if (ingestContribs == null) {
            log.error("No configuration found for Content Lake. Calls, if any, will fail.");
        } else {
            ingestAuthTokens = new HashMap<String, AuthenticationToken>();
            for (Map.Entry<String, IngestDescriptor> entry : ingestContribs.entrySet()) {
                IngestDescriptor desc = entry.getValue();
                AuthenticationToken token = new AuthenticationTokenIngestion(
                        desc.getAuthenticationBaseUrl() + AUTH_ENDPOINT, desc.getAuthenticationTokenParams());
                ingestAuthTokens.put(desc.getName(), token);

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

        // log.warn("Stop component");
    }

}
