/*
 * (C) Copyright 2025 Hyland (http://hyland.com/) and others.
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

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.labs.hyland.content.intelligence.ContentToProcess;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;

/**
 * ==================================================
 * JAN 2026: About KE v1->v2 compatibility and format
 * ==================================================
 * For compatibility KE v1-v2, the extra optional "instructions" KE-V2 are to be passed in the extraJsonPayloadStr
 * parameter of the misc. methods:
 * <ul>
 * <li>It must be in the "instructions" property of extraJsonPayloadStr
 * <li>It is an object of objects, one per action
 * </ul>
 * For example, when requesting textClassification and textSummarization, extraJsonPayloadStr could be:
 * 
 * <pre>
 * {
 *   "instructions": {
 *     "textClassification": {
 *       "context": "legal documents",
 *       . . .
 *     },
 *     "textSummarization: {
 *       "tone": "professional",
 *       . . .
 *     }
 *   }
 * }
 * </pre>
 * 
 * Also, actions that accept a maxWordCount => must be passed in extraJsonPayloadStr too. As this property is used only
 * for textSummarization and imageDescription, and a blob can't be both, it can be passed as a single value:
 * 
 * <pre>
 * {
 *   "instructions": {
 *     . . .
 *   },
 *   "maxWordCount": 200,
 *   . . .
 * }
 * </pre>
 * 
 * @since TODO
 */
@SuppressWarnings("rawtypes")
public interface HylandKEService {

    /**
     * Using KE v2 is global to every call. It is not possible to use v2 for a call, then v1 for another, etc.
     * It can be set at startup with the HylandKEServiceImpl#KE_USE_V2_PARAM configuration parameter
     * 
     * @param value
     * @since TODO
     */
    public void setUseKEV2(boolean value);

    /**
     * @return the value of current setting.
     * @since TODO
     */
    public boolean getUseKEV2();

    /**
     * Send the blob for enrichment. In the response, and if succesful, there will be the job ID to use with
     * getJobIdResult(). Also, <copde>sourceid</sourceId> is optional. I is returned in the result (see
     * ServiceCallResult#objectKeysMapping) and let the caller bind the job ID with the input blob.
     * If not passed, a random UUID is generated.
     * <br>
     * For the values to pass in <code>actions</code>, <code>classes</code> and <code>similarmetadata</code>, see
     * the service documentation at
     * {@link https://hyland.github.io/ContentIntelligence-Docs/KnowledgeEnrichment/ContextEnrichmentAPI}
     * <br>
     * configName is the contribution to read for authentication and misc. If null or "", we use "default" (contributed
     * by the plugin and using config. parameters)
     * 
     * @param configName
     * @param blob
     * @param sourceId
     * @param actions
     * @param classes
     * @param similarMetadataJsonArrayStr
     * @param extraJsonPayloadStr
     * @return
     * @throws IOException
     * @since 2023
     */
    public ServiceCallResult sendForEnrichment(String configName, Blob blob, String sourceId, List<String> actions,
            List<String> classes, String similarMetadataJsonArrayStr, String extraJsonPayloadStr) throws IOException;

    /**
     * Send the file for enrichment. In the response, and if succesful, there will be the job ID to use with
     * getJobIdResult(). Also, <copde>sourceid</sourceId> is optional. I is returned in the result (see
     * ServiceCallResult#objectKeysMapping) and let the caller bind the job ID with the input blob.
     * If not passed, a random UUID is generated.
     * <br>
     * For the values to pass in <code>actions</code>, <code>classes</code> and <code>similarmetadata</code>, see
     * the service documentation at
     * {@link https://hyland.github.io/ContentIntelligence-Docs/KnowledgeEnrichment/ContextEnrichmentAPI}
     * <br>
     * configName is the contribution to read for authentication and misc. If null or "", we use "default" (contributed
     * by the plugin and using config. parameters)
     * 
     * @param configName
     * @param file
     * @param sourceId
     * @param mimeType. If null or "", it will be calculated (can take time)
     * @param actions
     * @param classes
     * @param similarMetadataJsonArrayStr
     * @param extraJsonPayloadStr
     * @return
     * @throws IOException
     * @since 2023
     */
    public ServiceCallResult sendForEnrichment(String configName, File file, String sourceId, String mimeType,
            List<String> actions, List<String> classes, String similarMetadataJsonArrayStr, String extraJsonPayloadStr)
            throws IOException;

    /**
     * Send a list of blobs for enrichment. In the response, and if succesful, there will be the job ID to use with
     * getJobIdResult()
     * <br>
     * configName is the contribution to read for authentication and misc. If null or "", we use "default" (contributed
     * by the plugin and using config. parameters)
     * 
     * @param configName
     * @param contentObjects
     * @param actions
     * @param classes
     * @param similarMetadataJsonArrayStr
     * @param extraJsonPayloadStr
     * @return
     * @throws IOException
     * @since 2023
     */
    public ServiceCallResult sendForEnrichment(String configName, List<ContentToProcess> contentObjects,
            List<String> actions, List<String> classes, String similarMetadataJsonArrayStr, String extraJsonPayloadStr)
            throws IOException;

    /**
     * After calling one of the sendForEnrichment() method, pull the results with getJobIdResult().
     * The HTTP response mayb not be 200. it could be for example 202, "accepted"
     * <br>
     * configName is the contribution to read for authentication and misc. If null or "", we use "default" (contributed
     * by the plugin and using config. parameters)
     * 
     * @param configName
     * @param jobId
     * @return
     * @throws IOException
     * @since 2023
     */
    public ServiceCallResult getJobIdResult(String configName, String jobId) throws IOException;

    /**
     * High level call performing all the different serial requests to the service (authenticate, then ask for presigned
     * url, then send the file, etc.)
     * <br>
     * For the values to pass in <code>actions</code>, <code>classes</code> and <code>similarmetadata</code>, see
     * the service documentation at
     * {@link https://hyland.github.io/ContentIntelligence-Docs/KnowledgeEnrichment/ContextEnrichmentAPI}
     * <br>
     * configName is the contribution to read for authentication and misc. If null or "", we use "default" (contributed
     * by the plugin and using config. parameters)
     * 
     * @param configName
     * @param blob
     * @param actions
     * @param classes
     * @param similarMetadata
     * @return a ServiceCallResult
     * @throws IOException
     * @since 2023
     */
    public ServiceCallResult enrich(String configName, Blob blob, List<String> actions, List<String> classes,
            String similarMetadataJsonArrayStr, String extraJsonPayloadStr) throws IOException;

    /**
     * See method
     * <code>enrich(Blob blob, List<String> actions, List<String> classes, List<String> similarMetadata)</code>
     * <br>
     * configName is the contribution to read for authentication and misc. If null or "", we use "default" (contributed
     * by the plugin and using config. parameters)
     * 
     * @param configName
     * @param file
     * @param mimeType. If null or "", it will be calculated (can take time)
     * @param actions
     * @param classes
     * @param similarMetadata
     * @return a ServiceCallResult
     * @throws IOException
     * @since 2023
     */
    public ServiceCallResult enrich(String configName, File file, String mimeType, List<String> actions,
            List<String> classes, String similarMetadataJsonArrayStr, String extraJsonPayloadStr) throws IOException;

    /**
     * Enrich a list of blobs
     * <br>
     * configName is the contribution to read for authentication and misc. If null or "", we use "default" (contributed
     * by the plugin and using config. parameters)
     * 
     * @param configName
     * @param blobs
     * @param actions
     * @param classes
     * @param similarMetadataJsonArrayStr
     * @return
     * @throws IOException
     * @since 2023
     */
    public ServiceCallResult enrich(String configName, List<ContentToProcess> contentObjects, List<String> actions,
            List<String> classes, String similarMetadataJsonArrayStr, String extraJsonPayloadStr) throws IOException;

    /**
     * Call the KE service, using the configuration parameters (clientId, clientSecret, endpoints, …). This is a kind of
     * "low-level" call to the service.
     * <br>
     * The method handles the authentication token and its expiration time.
     * <br>
     * <code>jsonPayload</code> may be null, and its content depends on the <code>endpoint</code> used.
     * <br>
     * <code>endpoint</code> are documented here:
     * {@link https://hyland.github.io/ContentIntelligence-Docs/KnowledgeEnrichment/ContextEnrichmentAPI}
     * <br>
     * configName is the contribution to read for authentication and misc. If null or "", we use "default" (contributed
     * by the plugin and using config. parameters)
     * 
     * @param configName
     * @param httpMethod
     * @param endpoint
     * @param jsonPayload
     * @return a ServiceCallResult
     * @since 2023
     */
    public ServiceCallResult invokeEnrichment(String configName, String httpMethod, String endpoint,
            String jsonPayload);

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
     * @return the list of contributions for KE
     * @since 2023
     */
    public List<String> getContribNames();

    /**
     * Introspection
     */
    public KEDescriptor getKEDescriptor(String configName);

}
