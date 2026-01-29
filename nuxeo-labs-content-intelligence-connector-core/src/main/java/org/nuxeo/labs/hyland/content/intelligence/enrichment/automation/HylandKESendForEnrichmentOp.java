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
package org.nuxeo.labs.hyland.content.intelligence.enrichment.automation;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.HylandKEService;

@Operation(id = HylandKESendForEnrichmentOp.ID, category = "Hyland Knowledge Enrichment", label = "CIC Knowledge Enrichement Send Blob", description = ""
        + "Invoke the Hyland Knowledge Enrichment (KE) API to send the blob for enrichment. actions is a list of actions to process"
        + " (image-description, image-embeddings, …, or imageDescription, imageEmbeddings, … for KE v2), classes a list of values to be used for classification,"
        + " and similarValues is used for metadata endpoint. It must be passed as a. (See KE documentation for details, limitation, etc.)"
        + " The result is a JSON as string. If succesful, its response object will have a processingId property,"
        + " it is the value to pass to the HylandKnowledgeEnrichment.GetEnrichmentResults operation to actually get the results."
        + " sourceId is optional, it makes it possible to bind the result jobId to a document, for example, so you can get the"
        + " document when calling HylandKnowledgeEnrichment.GetEnrichmentResults."
        + " configName is the name of the XML configuration to use (if not passed, using 'default')"
        + " For KE V2 compatibility, if you want to pass the 'instructions' object, pass it in the extraJsonPayloadStr,"
        + " as an object of objects, one per action (if instructions are requested). See plugin doc.")
public class HylandKESendForEnrichmentOp {

    public static final String ID = "HylandKnowledgeEnrichment.SendForEnrichment";

    @Context
    protected HylandKEService keService;

    @Param(name = "sourceId", required = true)
    protected String sourceId;

    @Param(name = "actions", required = true)
    protected String actions;

    @Param(name = "classes", required = false)
    protected String classes = null;

    @Param(name = "similarMetadataJsonArrayStr", required = false)
    protected String similarMetadataJsonArrayStr = null;

    @Param(name = "extraJsonPayloadStr", required = false)
    protected String extraJsonPayloadStr = null;

    @Param(name = "configName", required = false)
    protected String configName;

    @OperationMethod
    public Blob run(Blob blob) {

        List<String> theActions = Arrays.stream(actions.split(",")).map(String::trim).toList();

        List<String> theClasses = null;
        if (StringUtils.isNotBlank(classes)) {
            theClasses = Arrays.stream(classes.split(",")).map(String::trim).toList();
        }

        ServiceCallResult result;
        try {
            result = keService.sendForEnrichment(configName, blob, sourceId, theActions, theClasses,
                    similarMetadataJsonArrayStr, extraJsonPayloadStr);
        } catch (IOException e) {
            throw new NuxeoException(e);
        }
        return Blobs.createJSONBlob(result.toJsonString());
    }

}
