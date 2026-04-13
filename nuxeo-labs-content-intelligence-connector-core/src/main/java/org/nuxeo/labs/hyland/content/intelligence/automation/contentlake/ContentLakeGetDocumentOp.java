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
package org.nuxeo.labs.hyland.content.intelligence.automation.contentlake;

import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.labs.hyland.content.intelligence.service.contentlake.ContentLakeService;

/**
 * @since TODO
 */
@Operation(id = ContentLakeGetDocumentOp.ID, category = "Hyland Content Lake", label = "Get a document in the Content Lake", description = ""
        + "input is a document, used to check its counterpart in Content Lake."
        + " Returns a JSON blob holding the result of the call. Call its getString() method then JSON.parse()."
        + " See plugin documentation for possible values of the returned blob."
        + " sourceId is optional. If not passed, we use nuxeo.hyland.cic.contentlake.default.sourceId."
        + " configName is the name of the XML configuration to use for authentication and baseUrl (if not passed, using 'default')")
public class ContentLakeGetDocumentOp {

    public static final String ID = "HylandIngest.CheckDigest";

    @Context
    protected ContentLakeService clService;

    @Param(name = "sourceId", required = false)
    protected String sourceId;

    @Param(name = "configName", required = false)
    protected String configName;

    @OperationMethod
    public Blob run(DocumentModel doc) {

        ServiceCallResult result = clService.getDocument(configName, doc, sourceId);

        return Blobs.createJSONBlob(result.toJsonString());
    }

}
