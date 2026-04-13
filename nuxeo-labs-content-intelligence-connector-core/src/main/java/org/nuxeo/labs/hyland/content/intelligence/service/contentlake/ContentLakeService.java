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
package org.nuxeo.labs.hyland.content.intelligence.service.contentlake;

import java.util.List;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;

/**
 * @since 2025.15/2023.18
 */
public interface ContentLakeService {
    
    public static final String SERVICE_LABEL = "Content Lake";

    /**
     * Return the document JSON from the ContentLake
     * A warning that it can be relatively big since it also returns the embeddings.
     * 
     * @param configName, optional. Null or "" => "default"
     * @param doc, required
     * @param sourceId, optional, the repository ID in ContentLkae.
     *        Default reads from nuxeo.hyland.cic.contentlake.sourceId
     * @return the result from the service
     */
    public ServiceCallResult getDocument(String configName, DocumentModel doc, String sourceId);
    
    public ServiceCallResult getDocument(String configName, String docId, String sourceId);

    /**
     * @return the list of contributions
     */
    public List<String> getContribNames();

    /**
     * Introspection
     */
    public ContentLakeDescriptor getDescriptor(String configName);

}
