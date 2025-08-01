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

/**
 * @since 2023
 */
@XObject("dataCuration")
public class DCDescriptor {

    private static final Logger log = LogManager.getLogger(DCDescriptor.class);

    @XNode("name")
    protected String name = null;

    @XNode("authenticationBaseUrl")
    protected String authenticationBaseUrl = null;

    @XNode("baseUrl")
    protected String baseUrl = null;

    @XNode("clientId")
    protected String clientId = null;

    @XNode("clientSecret")
    protected String clientSecret = null;

    public String getName() {
        return name;
    }

    public String getAuthenticationBaseUrl() {
        return authenticationBaseUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void checkConfigAndLogErrors() {

        if (StringUtils.isBlank(authenticationBaseUrl)) {
            log.warn("No CIC Authentication endpoint provided for configuration '" + name
                    + "', calls to the service will fail.");
        }

        if (StringUtils.isBlank(baseUrl)) {
            log.warn("No CIC Data Curation endpoint provided for configuration '" + name
                    + "', calls to the service will fail.");
        }

        if (StringUtils.isBlank(clientId)) {
            log.warn("No CIC Data Curation ClientId provided for configuration '" + name
                    + "', calls to the service will fail.");
        }

        if (StringUtils.isBlank(clientSecret)) {
            log.warn("No CIC Data Curation clientSecret provided for configuration '" + name
                    + "', calls to the service will fail.");
        }

    }

}
