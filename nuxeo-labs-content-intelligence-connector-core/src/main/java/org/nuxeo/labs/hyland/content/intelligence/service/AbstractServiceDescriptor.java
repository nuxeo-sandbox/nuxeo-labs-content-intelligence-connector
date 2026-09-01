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
package org.nuxeo.labs.hyland.content.intelligence.service;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.labs.hyland.content.intelligence.authentication.AuthenticationTokenParams;

/**
 * KD/KE/DC/... Descriptors have most of their fields in common
 * => centralizing everything so each descriptor stays simple (instead of duplicated code)
 */
public abstract class AbstractServiceDescriptor {

    @XNode("name")
    protected String name;

    @XNode("authenticationBaseUrl")
    protected String authenticationBaseUrl;

    @XNode("baseUrl")
    protected String baseUrl;

    @XNode("tokenGrantType")
    protected String tokenGrantType;

    @XNode("tokenScope")
    protected String tokenScope;

    @XNode("clientId")
    protected String clientId;

    @XNode("clientSecret")
    protected String clientSecret;

    protected AuthenticationTokenParams authTokenParams;

    protected abstract Logger log();

    protected abstract String serviceLabel();

    protected abstract boolean requiresEnvironment();

    public abstract String getEnvironment();

    public String getName() {
        return name;
    }

    public String getAuthenticationBaseUrl() {
        return authenticationBaseUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public AuthenticationTokenParams getAuthenticationTokenParams() {
        if (authTokenParams == null) {
            authTokenParams = new AuthenticationTokenParams(tokenGrantType, tokenScope, clientId, clientSecret,
                    getEnvironment());
        }
        return authTokenParams;
    }

    /**
     * Merges another contribution having the SAME name into this one.
     * <p>
     * Only <b>non-blank</b> values of {@code other} override the current ones. This is deliberate: a value can be
     * missing either because the XML element is absent ({@code null}) or because it resolved to an empty string (the
     * very common {@code ${some.undefined.config.param:=}} case). In both situations the contribution must NOT wipe
     * an already configured value.
     * <p>
     * This allows a partial override, typically from a Studio project, to add or change a couple of fields without
     * having to redeclare the whole configuration:
     *
     * <pre>
     * &lt;knowledgeEnrichment&gt;
     *   &lt;name&gt;default&lt;/name&gt;
     *   &lt;embeddingsFacet&gt;Embeddings&lt;/embeddingsFacet&gt;
     * &lt;/knowledgeEnrichment&gt;
     * </pre>
     * <p>
     * {@code name} is the map key and is identical by construction, so it is never merged.
     * <p>
     * <b>Subclasses declaring their own {@code @XNode} fields MUST override this method</b> and chain to
     * {@code super.merge(other)}, else those fields are silently lost when a configuration is overridden.
     *
     * @param other the contribution to merge into this one
     * @since 2025.20
     */
    public void merge(AbstractServiceDescriptor other) {

        if (other == null) {
            return;
        }

        if (StringUtils.isNotBlank(other.authenticationBaseUrl)) {
            authenticationBaseUrl = other.authenticationBaseUrl;
        }
        if (StringUtils.isNotBlank(other.baseUrl)) {
            baseUrl = other.baseUrl;
        }
        if (StringUtils.isNotBlank(other.tokenGrantType)) {
            tokenGrantType = other.tokenGrantType;
        }
        if (StringUtils.isNotBlank(other.tokenScope)) {
            tokenScope = other.tokenScope;
        }
        if (StringUtils.isNotBlank(other.clientId)) {
            clientId = other.clientId;
        }
        if (StringUtils.isNotBlank(other.clientSecret)) {
            clientSecret = other.clientSecret;
        }

        // Invalidate the lazily built cache, it would otherwise keep the pre-merge values
        authTokenParams = null;
    }

    /**
     * Returns the list of the mandatory fields that are missing (blank) in this configuration, as a comma separated
     * string. Returns an empty string when the configuration is complete.
     *
     * @since 2025.20
     */
    public String getMissingValuesAsString() {

        List<String> missing = new ArrayList<>();

        if (StringUtils.isBlank(authenticationBaseUrl)) {
            missing.add("authenticationBaseUrl");
        }
        if (StringUtils.isBlank(baseUrl)) {
            missing.add("baseUrl");
        }
        if (StringUtils.isBlank(tokenGrantType)) {
            missing.add("tokenGrantType");
        }
        if (StringUtils.isBlank(tokenScope)) {
            missing.add("tokenScope");
        }
        if (StringUtils.isBlank(clientId)) {
            missing.add("clientId");
        }
        if (StringUtils.isBlank(clientSecret)) {
            missing.add("clientSecret");
        }
        if (requiresEnvironment() && StringUtils.isBlank(getEnvironment())) {
            missing.add("environment");
        }

        return String.join(", ", missing);
    }

    public boolean hasAllValues() {
        if (StringUtils.isBlank(authenticationBaseUrl) || StringUtils.isBlank(baseUrl)
                || StringUtils.isBlank(tokenGrantType) || StringUtils.isBlank(tokenScope)
                || StringUtils.isBlank(clientId) || StringUtils.isBlank(clientSecret)) {
            return false;
        }
        // si un descriptor a besoin d’environment, il override environment() non-null
        if (requiresEnvironment() && StringUtils.isBlank(getEnvironment())) {
            return false;
        }
        return true;
    }

    /**
     * @deprecated since 2025.20, superseded by {@link #getMissingValuesAsString()}, which produces one single
     *             actionable message instead of up to seven separate WARN lines.
     */
    @Deprecated
    public void checkConfigAndLogErrors() {
        final String serviceLabel = serviceLabel();

        if (StringUtils.isBlank(authenticationBaseUrl)) {
            log().warn(
                    "No CIC Authentication endpoint provided for configuration '{}', authentication to {} will fail.",
                    name, serviceLabel);
        }
        if (StringUtils.isBlank(baseUrl)) {
            log().warn("No CIC {} endpoint provided for configuration '{}', calls to the service will fail.", serviceLabel,
                    name);
        }
        if (StringUtils.isBlank(tokenGrantType)) {
            log().warn(
                    "No CIC {} tokenGrantType provided for configuration '{}', authentication to the service will fail.",
                    serviceLabel, name);
        }
        if (StringUtils.isBlank(tokenScope)) {
            log().warn("No CIC {} tokenScope provided for configuration '{}', authentication to the service will fail.",
                    serviceLabel, name);
        }
        if (StringUtils.isBlank(clientId)) {
            log().warn("No CIC {} clientId provided for configuration '{}', authentication to the service will fail.",
                    serviceLabel, name);
        }
        if (StringUtils.isBlank(clientSecret)) {
            log().warn(
                    "No CIC {} clientSecret provided for configuration '{}', authentication to the service will fail.",
                    serviceLabel, name);
        }
        if (requiresEnvironment() && StringUtils.isBlank(getEnvironment())) {
            log().warn("No CIC {} environment provided for configuration '{}', calls to the service will fail.", serviceLabel,
                    name);
        }
    }
}
