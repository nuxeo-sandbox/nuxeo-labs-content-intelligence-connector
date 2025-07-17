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

import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.HylandKEService;

@Operation(id = HylandKECurateOp.ID, category = "Hyland Knowledge Enrichment", label = "CIC Data Curation on Blob", description = ""
        + "Invoke the Hyland Data Curation (DC) API to curate the blob. jsonOptions is optional, a JSON string"
        + " that will tune the result.(See DC documentation for details, limitation, etc.)."
        + " configName is the name of the XML configuration to use (if not passed, using 'default')")
public class HylandKECurateOp {

    public static final String ID = "HylandKnowledgeEnrichment.Curate";

    @Context
    protected HylandKEService keService;

    @Param(name = "configName", required = false)
    protected String configName;

    @Param(name = "jsonOptions", required = false)
    protected String jsonOptions;

    @OperationMethod
    public Blob run(Blob blob) {

        ServiceCallResult result;
        try {
            result = keService.curate(configName, blob, jsonOptions);
        } catch (IOException e) {
            throw new NuxeoException(e);
        }
        return Blobs.createJSONBlob(result.toJsonString());
    }

}
