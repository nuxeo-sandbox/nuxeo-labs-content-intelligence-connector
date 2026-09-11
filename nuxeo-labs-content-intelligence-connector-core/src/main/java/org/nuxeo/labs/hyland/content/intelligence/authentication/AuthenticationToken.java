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
     * Tells whether this service needs an {@code environment} value at all.
     * <p>
     * Discovery and Ingest send it as an HTTP header; Content Lake uses it to build the host name of its base URL
     * ({@code https://<environment>.<baseUrl>}). In all three cases a missing value makes the calls fail, so it
     * must be validated. Keep this in sync with {@code requiresEnvironment()} on the descriptors.
     *
     * @since 2025.22
     */
    protected boolean requiresEnvironment() {
        return serviceType == ServiceType.DISCOVERY || serviceType == ServiceType.INGEST
                || serviceType == ServiceType.CONTENTLAKE;
    }

    /**
     * Tells whether the {@code hxp-environment} header must be sent on the <b>authentication</b> request.
     * <p>
     * Deliberately narrower than {@link #requiresEnvironment()}: Content Lake needs an environment but has never
     * been sent this header, and adding it would change a request that currently works.
     *
     * @since 2025.22
     */
    protected boolean requiresEnvironmentHeader() {
        return serviceType == ServiceType.DISCOVERY || serviceType == ServiceType.INGEST;
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
            if (requiresEnvironment() && StringUtils.isBlank(tokenParams.getEnvironment())) {
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
     * Returns a valid token, fetching a new one only when the current one is missing or expired.
     * <p>
     * <b>Synchronized on purpose.</b> A single instance of this class is shared, through a static map held by
     * each service component, by every thread that talks to Content Intelligence: Automation HTTP threads, the
     * {@code cicEnrichment} Work threads and asynchronous listeners. Without this, two problems appeared.
     * {@code token} and {@code tokenExpiration} are written in sequence and were neither volatile nor guarded, so
     * another thread could observe a non-null {@code token} together with a still-null {@code tokenExpiration}
     * and fail on {@code Instant.isAfter(null)} with a bare NullPointerException. And on expiry every concurrent
     * thread fired its own authentication request, for nothing.
     * <p>
     * The cost is irrelevant: the lock is only ever contended around a call that is itself an HTTP round trip.
     *
     * @return the authentication token, or {@code null} when it could not be obtained
     * @since 2023
     */
    public synchronized String getToken() {

        if (StringUtils.isNotBlank(token) && tokenExpiration != null && !Instant.now().isAfter(tokenExpiration)) {
            return token;
        }

        checkConfigOrThrow();

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "*/*");
        headers.put("Accept-Encoding", "gzip, deflate, br");
        if (requiresEnvironmentHeader()) {
            headers.put("hxp-environment", tokenParams.getEnvironment());
        }
        // Not JSON...
        headers.put("Content-Type", "application/x-www-form-urlencoded");

        // Request body
        String postData = "client_id=" + URLEncoder.encode(tokenParams.getClientId(), StandardCharsets.UTF_8);
        postData += "&client_secret=" + URLEncoder.encode(tokenParams.getClientSecret(), StandardCharsets.UTF_8);
        postData += "&grant_type=" + URLEncoder.encode(tokenParams.getGrantType(), StandardCharsets.UTF_8);
        postData += "&scope=" + URLEncoder.encode(tokenParams.getGrantScope(), StandardCharsets.UTF_8);

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
                /*
                 * The previous token, if any, is necessarily expired at this point (we only get here when it is),
                 * so it must be dropped. Returning it would hand the caller a token that can only produce a 401,
                 * with an error message unrelated to the real cause, which is in this log line.
                 */
                invalidate();
            } else {
                token = serviceResponse.getString("access_token");
                int expiresIn = serviceResponse.getInt("expires_in");
                tokenExpiration = Instant.now().plusSeconds(expiresIn - 15);
            }
        } else {
            log.error("Error getting an auth token:\n{}", result.toJsonString(2));
            invalidate();
        }

        return token;

    }

    /** Drops the cached token, keeping both fields consistent. */
    protected void invalidate() {
        token = null;
        tokenExpiration = null;
    }

}
