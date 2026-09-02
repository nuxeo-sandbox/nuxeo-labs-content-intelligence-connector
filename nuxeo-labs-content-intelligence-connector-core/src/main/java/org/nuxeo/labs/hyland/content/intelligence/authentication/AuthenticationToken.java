/*
 * (C) Copyright 2025 Hyland (http://hyland.com/)  and others.
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
package org.nuxeo.labs.hyland.content.intelligence.authentication;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCall;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;

/**
 * This class handles authentication tokens and their lifespan. If a token was requested before expiration, it is
 * returned as is. Else, a new token is fetched.
 *
 * @since 2023
 */
public class AuthenticationToken {

    private static final Logger log = LogManager.getLogger(AuthenticationToken.class);

    protected String token = null;

    protected Instant tokenExpiration = null;

    protected String authFullUrl;

    protected AuthenticationTokenParams tokenParams;

    protected ServiceCall serviceCall = new ServiceCall();

    public enum ServiceType {
        ENRICHMENT, DISCOVERY, AGENTS, INGEST, CONTENTLAKE
    }

    ServiceType serviceType;

    /** The name of the contribution this token belongs to, used to build actionable error messages. @since 2025.20 */
    protected String configName;

    public AuthenticationToken(ServiceType serviceType, String authFullUrl, AuthenticationTokenParams params) {
        this(serviceType, authFullUrl, params, null);
    }

    /**
     * @param configName the name of the contribution this token belongs to
     * @since 2025.20
     */
    public AuthenticationToken(ServiceType serviceType, String authFullUrl, AuthenticationTokenParams params,
            String configName) {

        this.serviceType = serviceType;

        this.authFullUrl = authFullUrl;
        this.tokenParams = params;
        this.configName = configName;

    }

    /**
     * Fails fast, with an actionable message, when the configuration is unusable.
     * <p>
     * Without this check the missing values reach the request body, where {@code URLEncoder.encode} throws a bare
     * {@code NullPointerException} for {@code clientId}/{@code clientSecret}, and where {@code grantType}/
     * {@code grantScope} are silently concatenated as the literal string {@code "null"}.
     *
     * @since 2025.20
     */
    protected void checkConfigOrThrow() {

        List<String> missing = new ArrayList<>();

        if (StringUtils.isBlank(authFullUrl)) {
            missing.add("authenticationBaseUrl");
        }
        if (tokenParams == null) {
            missing.add("all authentication parameters");
        } else {
            if (StringUtils.isBlank(tokenParams.getClientId())) {
                missing.add("clientId");
            }
            if (StringUtils.isBlank(tokenParams.getClientSecret())) {
                missing.add("clientSecret");
            }
            if (StringUtils.isBlank(tokenParams.getGrantType())) {
                missing.add("tokenGrantType");
            }
            if (StringUtils.isBlank(tokenParams.getGrantScope())) {
                missing.add("tokenScope");
            }
            if ((serviceType == ServiceType.DISCOVERY || serviceType == ServiceType.INGEST)
                    && StringUtils.isBlank(tokenParams.getEnvironment())) {
                missing.add("environment");
            }
        }

        if (!missing.isEmpty()) {
            throw new NuxeoException("Cannot authenticate to the CIC " + serviceType + " service using configuration '"
                    + (configName == null ? "default" : configName) + "': missing value(s): "
                    + String.join(", ", missing)
                    + ". Check the corresponding nuxeo.conf parameters and the XML contribution."
                    + " Note that contributing a configuration with an already existing name only overrides the fields"
                    + " you declare, the other ones are kept.");
        }
    }

    /**
     * Will fetch a new token only if the current token is null or expired.
     *
     * @param url, the full authentication URL
     * @return the authentication token
     * @since 2023
     */
    public String getToken() {

        if (StringUtils.isNotBlank(token) && !Instant.now().isAfter(tokenExpiration)) {
            return token;
        }

        checkConfigOrThrow();

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "*/*");
        headers.put("Accept-Encoding", "gzip, deflate, br");
        if (serviceType == ServiceType.DISCOVERY || serviceType == ServiceType.INGEST) {
            headers.put("hxp-environment", tokenParams.getEnvironment());
        }
        // Not JSON...
        headers.put("Content-Type", "application/x-www-form-urlencoded");

        // Request body
        String postData = "client_id=" + URLEncoder.encode(tokenParams.getClientId(), StandardCharsets.UTF_8);
        postData += "&client_secret=" + URLEncoder.encode(tokenParams.getClientSecret(), StandardCharsets.UTF_8);
        postData += "&grant_type=" + URLEncoder.encode(tokenParams.getGrantType(), StandardCharsets.UTF_8);
        postData += "&scope=" + URLEncoder.encode(tokenParams.grantScope, StandardCharsets.UTF_8);

        ServiceCallResult result = serviceCall.post(authFullUrl, headers, postData);

        if (result.callWasSuccesful()) {
            JSONObject serviceResponse = result.getResponseAsJSONObject();
            // {"error":"invalid_grant","error_description":"Caller not authorized for requested resource"}
            if (serviceResponse.has("error")) {
                String msg = "Getting a token failed with error " + serviceResponse.getString("error") + ".";
                if (serviceResponse.has("error_description")) {
                    msg += " " + serviceResponse.getString("error_description");
                }
                log.error(msg);
            } else {
                token = serviceResponse.getString("access_token");
                int expiresIn = serviceResponse.getInt("expires_in");
                tokenExpiration = Instant.now().plusSeconds(expiresIn - 15);
            }
        } else {
            log.error("Error getting an auth token:\n{}", result.toJsonString(2));
            token = null;
        }

        return token;

    }

}
