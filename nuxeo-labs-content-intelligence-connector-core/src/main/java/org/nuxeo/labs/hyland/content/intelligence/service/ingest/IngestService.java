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
package org.nuxeo.labs.hyland.content.intelligence.service.ingest;

import java.util.List;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;

/**
 * @since 2025.15/2023.18
 */
public interface IngestService {

    /**
     * For all checkDigest() methods, they return a ServiceCall result.
     * - Success => responseCode is 200, and the response is {"exists": true/false}
     * - Failure: check response code (403, forbidden, 404, not found, etc.)
     * <br>
     * Check if the digest exists in the ContentLake, using the defauilt sourceId repository
     * 
     * @param configName, optional. Null or "" => "default"
     * @param doc, required
     * @param xpath, optional. "file:content" by default
     * @return the result from the service
     */
    public ServiceCallResult checkDigest(String configName, DocumentModel doc, String xpath);

    /**
     * Check if the digest exists in the ContentLake, in the sourceId repository
     * 
     * @param configName, optional. Null or "" => "default"
     * @param doc, required
     * @param xpath, optional. "file:content" by default
     * @paral sourceId, optional, the repository ID in ContentLkae.
     *        Default reads from nuxeo.hyland.cic.contentlake.sourceId
     * @return the result from the service
     */
    public ServiceCallResult checkDigest(String configName, DocumentModel doc, String xpath, String sourceId);

    /**
     * Check if the digest exists in the ContentLake, in the sourceId repository
     * 
     * @param configName
     * @param docId
     * @param blobDigest
     * @param sourceId
     * @return the result from the service
     */
    public ServiceCallResult checkDigest(String configName, String docId, String blobDigest, String sourceId);

    /**
     * @return the list of contributions
     */
    public List<String> getContribNames();

    /**
     * Introspection
     */
    public IngestDescriptor getDescriptor(String configName);

}
