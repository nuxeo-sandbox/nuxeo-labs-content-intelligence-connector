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
package org.nuxeo.labs.hyland.content.intelligence.automation;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.labs.hyland.content.intelligence.service.agents.HylandAgentsService;
import org.nuxeo.labs.hyland.content.intelligence.service.datacuration.HylandDCService;
import org.nuxeo.labs.hyland.content.intelligence.service.discovery.HylandKDService;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.HylandKEService;
import org.nuxeo.labs.hyland.content.intelligence.service.ingest.IngestService;

/**
 * 
 * @since TODO
 */
@Operation(id = HylandCIGetContributionNamesOp.ID, category = "Hyland Content Intelligence Connector", label = "Get the names of all contributions", description = ""
        + "Returns a JSON object (as string) with the names of the extension points and for exah of them, the name of the contriutions.")
public class HylandCIGetContributionNamesOp {
    
    public static final String ID = "HylandContentIntelligence.GetContributionNames";
    
    @Context
    protected HylandKEService keService;
    
    @Context
    protected HylandDCService dcService;
    
    @Context
    protected HylandKDService kdService;
    
    @Context
    protected HylandAgentsService agentsService;
    
    @Context
    protected IngestService ingestService;
    
    @OperationMethod
    public Blob run() {
        
        List<String> contribs;
        JSONArray contribsJson;
        
        JSONObject result = new JSONObject();
        
        contribs = keService.getContribNames();
        contribsJson = new JSONArray(contribs);
        result.put("knowledgeEnrichment", contribsJson);
        
        contribs = dcService.getContribNames();
        contribsJson = new JSONArray(contribs);
        result.put("dataCuration", contribsJson);
        
        contribs = kdService.getContribNames();
        contribsJson = new JSONArray(contribs);
        result.put("knowledgeDiscovery", contribsJson);
        
        contribs = agentsService.getContribNames();
        contribsJson = new JSONArray(contribs);
        result.put("agents", contribsJson);
        
        contribs = ingestService.getContribNames();
        contribsJson = new JSONArray(contribs);
        result.put("ingest", contribsJson);
        
        return Blobs.createJSONBlob(result.toString());
    }

}
