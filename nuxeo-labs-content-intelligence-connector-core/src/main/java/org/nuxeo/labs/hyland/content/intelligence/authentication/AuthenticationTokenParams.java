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
package org.nuxeo.labs.hyland.content.intelligence.authentication;

/**
 * The services change regularly what it expects in terms of headers or body value
 * (like the scope). We make it more configurable, so users can just tune their XML
 * instead of waiting for a new, hard coded version => See the mmisc. descriptors,
 * like {@link KDDescriptor} {@link KEDescriptor}, etc.
 */
public class AuthenticationTokenParams {

    protected String grantType;

    protected String grantScope;

    protected String clientId;

    protected String clientSecret;

    // Specific for KD
    protected String environment;

    public AuthenticationTokenParams(String grantType, String grantScope, String clientId, String clientSecret,
            String environment) {
        super();
        this.grantType = grantType;
        this.grantScope = grantScope;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.environment = environment;
    }

    public String getGrantType() {
        return grantType;
    }

    public String getGrantScope() {
        return grantScope;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getEnvironment() {
        return environment;
    }

}
