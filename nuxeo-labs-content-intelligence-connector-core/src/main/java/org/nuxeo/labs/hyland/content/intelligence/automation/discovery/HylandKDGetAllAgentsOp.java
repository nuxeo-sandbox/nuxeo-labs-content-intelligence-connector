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
package org.nuxeo.labs.hyland.content.intelligence.automation.discovery;

import java.util.Map;

import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.labs.hyland.content.intelligence.service.ServicesUtils;
import org.nuxeo.labs.hyland.content.intelligence.service.discovery.HylandKDService;

/**
 * 
 * @since TODO
 */
@Operation(id = HylandKDGetAllAgentsOp.ID, category = "Hyland Knowledge Discovery", label = "Get All KD Agents", description = ""
        + "Returns a JSON blob holding  the result of the call. Call its getString() method then JSON.parse()."
        + " See documentation for values. The result will have a 'responseCode' property that you should check (must be 200),"
        + " and the array of KD agents is in the 'response' property."
        + " You can also pass extra headers in extraHeadersJsonStr as a stringified Json object"
        + " configName is the name of the XML configuration to use (if not passed, using 'default')")
public class HylandKDGetAllAgentsOp {
    
    public static final String ID = "HylandKnowledgeDiscovery.getAllAgents";
    
    @Context
    protected HylandKDService kdService;

    @Param(name = "extraHeadersJsonStr", required = false)
    protected String extraHeadersJsonStr;

    @Param(name = "configName", required = false)
    protected String configName;
    
    @OperationMethod
    public Blob run() {
        
        Map<String, String> extraHeaders = ServicesUtils.jsonObjectStrToMap(extraHeadersJsonStr);
        
        ServiceCallResult result = kdService.getAllAgents(configName, extraHeaders);
        
        return Blobs.createJSONBlob(result.toJsonString());
    }

}
