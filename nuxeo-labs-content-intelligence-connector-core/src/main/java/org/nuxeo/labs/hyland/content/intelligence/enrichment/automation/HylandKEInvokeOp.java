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

import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.HylandKEService;

@Operation(id = HylandKEInvokeOp.ID, category = "Hyland Knowledge Enrichment", label = "Call Hyland Knowledge Enrichment Service", description = ""
        + "Invoke the Hyland Content Intelligence/Knowledge Enrichment API."
        + " Used for the low-level calls. (See Knowledge Enrichment API documentation for details)"
        + " configName is the name of the XML configuration to use (if not passed, using 'default')")
public class HylandKEInvokeOp {

    public static final String ID = "HylandKnowledgeEnrichment.Invoke";

    @Context
    protected HylandKEService keService;

    @Param(name = "httpMethod", required = true)
    protected String httpMethod;
    
    @Param(name = "endpoint", required = true)
    protected String endpoint;

    @Param(name = "jsonPayload", required = false)
    protected String jsonPayload;

    @Param(name = "configName", required = false)
    protected String configName;

    @OperationMethod
    public Blob run() {
        ServiceCallResult result = keService.invokeEnrichment(configName, httpMethod, endpoint, jsonPayload);
        
        return Blobs.createJSONBlob(result.toJsonString());
    }

}
