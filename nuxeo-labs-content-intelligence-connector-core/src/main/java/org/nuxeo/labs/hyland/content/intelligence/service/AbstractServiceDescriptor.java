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
 *     Thibaud Arguillere
 */
package org.nuxeo.labs.hyland.content.intelligence.service;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.labs.hyland.content.intelligence.AuthenticationTokenParams;

/**
 * KD/KE/DC Descriptors had most of their fields in common => centralizing evcerything so
 * each descriptor stays simple (instead of duplicated code)
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
