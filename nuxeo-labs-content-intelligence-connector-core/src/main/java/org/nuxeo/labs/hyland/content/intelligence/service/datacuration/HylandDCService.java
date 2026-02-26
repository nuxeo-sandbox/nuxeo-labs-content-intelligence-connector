/*
 * (C) Copyright 2026 Hyland (http://hyland.com/) and others.
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
package org.nuxeo.labs.hyland.content.intelligence.service.datacuration;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;

/**
 * 
 * @since 2025.15/2023.18
 */
public interface HylandDCService {

    /**
     * Call the Data Curation API and returns the curated result.
     * Implementation should provide default values if jsonOptions is null or "".
     * For possible values, please see the service documentation at
     * {@link https://hyland.github.io/ContentIntelligence-Docs/KnowledgeEnrichment/DataCurationAPI}
     * <br>
     * The returned String is JSON String with 3 fields:
     * {
     * "responseCode": The HTTP response of the service when performing the call. Should be a successful range (200-299)
     * "responseMessage": The HTTP response message (like "OK")
     * "response": The response (as JSON string from the service
     * }
     * <br>
     * configName is the contribution to read for authentication and misc. If null or "", we use "default" (contributed
     * by the plugin and using config. parameters)
     * 
     * @param configName
     * @param blob
     * @param jsonOptions
     * @return a ServiceCallResult
     * @throws IOException
     * @since 2023
     */
    public ServiceCallResult curate(String configName, Blob blob, String jsonOptions) throws IOException;

    /**
     * (see <code>curate(Blob blob, String jsonOptions)</code>
     * <br>
     * configName is the contribution to read for authentication and misc. If null or "", we use "default" (contributed
     * by the plugin and using config. parameters)
     * 
     * @param configName
     * @param file
     * @param jsonOptions
     * @return
     * @throws IOException
     * @since 2023
     */
    public ServiceCallResult curate(String configName, File file, String jsonOptions) throws IOException;

    /**
     * maxRetries and sleepInterval are configurations, they can be tuned with this method.
     * <br>
     * The value is used for all and every calls until they change again.
     * <br>
     * Special values:
     * <ul>
     * <li>0: Revert to configuration parameter values. If not set, revert to default value.</li>
     * <li>-1: Do not change the value</li>
     * </ul>
     * 
     * @param maxTries
     * @param sleepIntervalMS
     * @since 2023
     */
    public void setPullResultsSettings(int maxTries, int sleepIntervalMS);

    /**
     * @return the list of contributions for DC
     * @since 2023
     */
    public List<String> getContribNames();

    /**
     * Introspection
     */
    public DCDescriptor getDCDescriptor(String configName);

}
