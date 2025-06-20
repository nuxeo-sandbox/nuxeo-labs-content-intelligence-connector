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
package org.nuxeo.labs.hyland.content.intelligence.discovery.automation;

import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.labs.hyland.content.intelligence.discovery.service.HylandKDService;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;

/**
 * 
 * @since TODO
 */
@Operation(id = HylandKDInvokeOp.ID, category = "Hyland Knowledge Discovery", label = "Call Hyland Knowledge Discovery Service", description = ""
        + "Invoke the Hyland Content Intelligence/Discovery API."
        + " Used for the low-level calls. (See Discovery API documentation for details)")
public class HylandKDInvokeOp {
    
    public static final String ID = "HylandKnowledgeDiscovery.Invoke";

    @Param(name = "httpMethod", required = true)
    protected String httpMethod;
    
    @Param(name = "endpoint", required = true)
    protected String endpoint;

    @Param(name = "jsonPayload", required = false)
    protected String jsonPayload;
    
    @Context
    protected HylandKDService kdService;
    
    @OperationMethod
    public Blob run() {
        
        ServiceCallResult result = kdService.invokeDiscovery(httpMethod, endpoint, jsonPayload);
        
        return Blobs.createJSONBlob(result.toJsonString());
    }

}
