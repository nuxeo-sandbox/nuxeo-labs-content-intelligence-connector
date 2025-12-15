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
package org.nuxeo.labs.hyland.content.intelligence.service.enrichment;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.labs.hyland.content.intelligence.AuthenticationTokenParams;

/**
 * @since 2023
 */
@XObject("knowledgeEnrichment")
public class KEDescriptor {

    private static final Logger log = LogManager.getLogger(KEDescriptor.class);

    @XNode("name")
    protected String name = null;

    @XNode("authenticationBaseUrl")
    protected String authenticationBaseUrl = null;

    @XNode("baseUrl")
    protected String baseUrl = null;

    @XNode("tokenGrantType")
    protected String tokenGrantType = null;

    @XNode("tokenScope")
    protected String tokenScope = null;

    @XNode("clientId")
    protected String clientId = null;

    @XNode("clientSecret")
    protected String clientSecret = null;

    protected AuthenticationTokenParams authTokenParams = null;

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
            authTokenParams = new AuthenticationTokenParams(tokenGrantType, tokenScope, clientId, clientSecret, null);
        }

        return authTokenParams;
    }

    public boolean hasAllValues() {

        if (StringUtils.isBlank(authenticationBaseUrl) || StringUtils.isBlank(baseUrl)
                || StringUtils.isBlank(tokenGrantType) || StringUtils.isBlank(tokenScope)
                || StringUtils.isBlank(clientId) || StringUtils.isBlank(clientSecret)) {
            return false;
        }

        return true;
    }

    public void checkConfigAndLogErrors() {

        if (StringUtils.isBlank(authenticationBaseUrl)) {
            log.warn("No CIC Authentication endpoint provided for configuration '" + name
                    + "', authentication to the service will fail.");
        }

        if (StringUtils.isBlank(baseUrl)) {
            log.warn("No CIC Enrichment endpoint provided for configuration '" + name
                    + "', calls to the service will fail.");
        }

        if (StringUtils.isBlank(tokenGrantType)) {
            log.warn("No CIC Enrichment tokenGrantType provided in the configuration '" + name
                    + "', authentication to the service will fail.");
        }

        if (StringUtils.isBlank(tokenScope)) {
            log.warn("No CIC Enrichment tokenScope provided in the configuration '" + name
                    + "', authentication to the service will fail.");
        }

        if (StringUtils.isBlank(clientId)) {
            log.warn("No CIC Enrichment ClientId provided for configuration '" + name
                    + "', authentication to the service will fail.");
        }

        if (StringUtils.isBlank(clientSecret)) {
            log.warn("No CIC Enrichment clientSecret provided for configuration '" + name
                    + "', authentication to the service will fail.");
        }

    }

}
