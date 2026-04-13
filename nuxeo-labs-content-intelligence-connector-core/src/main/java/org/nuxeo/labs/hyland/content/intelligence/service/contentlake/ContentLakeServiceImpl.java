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
package org.nuxeo.labs.hyland.content.intelligence.service.contentlake;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.labs.hyland.content.intelligence.authentication.AuthenticationToken;
import org.nuxeo.labs.hyland.content.intelligence.authentication.AuthenticationTokenContentLake;
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
public class ContentLakeServiceImpl extends AbstractCICServiceComponent<ContentLakeDescriptor>
        implements ContentLakeService {

    private static final Logger log = LogManager.getLogger(ContentLakeServiceImpl.class);

    public static final String CONTENTLAKE_CLIENT_ID_PARAM = "nuxeo.hyland.cic.contentlake.clientId";

    public static final String CONTENTLAKE_CLIENT_SECRET_PARAM = "nuxeo.hyland.cic.contentlake.clientSecret";

    public static final String CONTENTLAKE_BASE_URL_PARAM = "nuxeo.hyland.cic.contentlake.baseUrl";

    public static final String CONTENTLAKE_ENVIRONMENT_PARAM = "nuxeo.hyland.cic.contentlake.environment";

    public static final String CONTENTLAKE_DEFAULT_SOURCEID_PARAM = "nuxeo.hyland.cic.contentlake.default.sourceId";
    
    public static final String CIN_DOC_ID_SEPARATOR = "__";

    protected static Map<String, AuthenticationToken> clAuthTokens = null;

    protected static String defaultSourceId;

    protected static ServiceCall serviceCall = new ServiceCall();

    // ====================> Extensions points
    public static final String EXT_POINT_CONTENTLAKE = "contentLake";

    // ====================> Endpoint(s)
    public static final String CL_DOCUMENTS_ENDPOINT = "/api/documents";

    // ======================================================================
    // ======================================================================
    // Internal
    // ======================================================================
    // ======================================================================
    public ContentLakeServiceImpl() {
        initialize();
    }

    protected void initialize() {
        defaultSourceId = Framework.getProperty(CONTENTLAKE_DEFAULT_SOURCEID_PARAM);

        // We don't want a WARN, this is an INFO
        String msg = "Startup configuration:";
        msg += "\n  defaultSourceId=" + defaultSourceId;
        ServicesUtils.forceLogInfo(this.getClass(), msg);
    }

    protected String getToken(String configName) {
        return super.getToken(clAuthTokens, configName);
    }

    protected String buildBaseUrl(String configName) {
        ContentLakeDescriptor config = getDescriptor(configName);
        String url = "https://" + config.getEnvironment() + "." + config.getBaseUrl();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    protected String buildCLDocId(String sourceId, String docId) {

        if (StringUtils.isBlank(sourceId)) {
            sourceId = defaultSourceId;
        }

        return sourceId + CIN_DOC_ID_SEPARATOR + docId;
    }

    @Override
    public String getDescriptorExtensionPoint() {
        return EXT_POINT_CONTENTLAKE;
    }

    @Override
    public String getServiceLabel() {
        return ContentLakeService.SERVICE_LABEL;
    }

    // ======================================================================
    // ======================================================================
    // Service Methods
    // ======================================================================
    // ======================================================================
    @Override
    public ServiceCallResult getDocument(String configName, DocumentModel doc, String sourceId) {

        return getDocument(configName, doc.getId(), sourceId);
    }

    @Override
    public ServiceCallResult getDocument(String configName, String docId, String sourceId) {

        if (StringUtils.isBlank(sourceId)) {
            sourceId = defaultSourceId;
        }

        // Get auth token
        String bearer = getToken(configName);
        if (StringUtils.isBlank(bearer)) {
            log.error("No authentication info for calling the Content Lake service.");
            return new ServiceCallResult("{}", -1, "No authentication info for calling the Content Lake service.");
        }

        String targetUrl = buildBaseUrl(configName) + CL_DOCUMENTS_ENDPOINT + "/" + buildCLDocId(sourceId, docId);
        // Headers
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer " + bearer);

        // Call
        ServiceCallResult result = serviceCall.get(targetUrl, headers);

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
    public ContentLakeDescriptor getDescriptor(String configName) {
        return super.getDescriptor(configName);
    }

    /**
     * Start the component. This method is called after all the components were resolved and activated
     *
     * @param context the component context. Use it to get the current bundle context
     */
    @Override
    public void start(ComponentContext context) {

        clAuthTokens = initAuthTokens(desc -> new AuthenticationTokenContentLake(
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
