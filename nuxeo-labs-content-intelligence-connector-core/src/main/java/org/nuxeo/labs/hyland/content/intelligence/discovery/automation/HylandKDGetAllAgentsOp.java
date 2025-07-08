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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
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
@Operation(id = HylandKDGetAllAgentsOp.ID, category = "Hyland Knowledge Discovery", label = "Get All Agents", description = ""
        + "Returns a JSON blob holding  the result of the call. Call its getString() method then JSON.parse()."
        + " See documentation for values. The result will have a responseCode that you should check (must be 200),"
        + " and the array of agents is in the response property."
        + " You can pass extra headers as a Json object (stringified)")
public class HylandKDGetAllAgentsOp {
    
    public static final String ID = "HylandKnowledgeDiscovery.getAllAgents";
    
    @Context
    protected HylandKDService kdService;

    @Param(name = "extraHeadersJsonStr", required = true)
    protected String extraHeadersJsonStr;
    
    @OperationMethod
    public Blob run() {
        
        Map<String, String> extraHeaders = null;
        if(StringUtils.isNotBlank(extraHeadersJsonStr)) {
            JSONObject extraHeadersJson = new JSONObject(extraHeadersJsonStr);
            extraHeaders = new HashMap<>();
            Iterator<String> keys = extraHeadersJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                extraHeaders.put(key, extraHeadersJson.getString(key));
            }
        }
        
        ServiceCallResult result = kdService.getAllAgents(extraHeaders);
        
        return Blobs.createJSONBlob(result.toJsonString());
    }

}
