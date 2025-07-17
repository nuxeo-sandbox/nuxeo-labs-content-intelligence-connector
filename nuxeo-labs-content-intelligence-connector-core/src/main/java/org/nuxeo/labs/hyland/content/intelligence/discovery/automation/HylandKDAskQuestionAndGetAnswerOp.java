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

import java.util.ArrayList;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
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
 * @since TODO
 */
@Operation(id = HylandKDAskQuestionAndGetAnswerOp.ID, category = "Hyland Knowledge Discovery", label = "Ask Question and Get Answer", description = ""
        + "Returns a JSON blob holding  the result of the call. Call its getString() method then JSON.parse()."
        + " See documentation for values. The result will have a 'responseCode' property that you should check (must be 200),"
        + " and the returned result is in the 'response' property." + " Ask a question, with optional parameters:"
        + " agenId => If empty, it is  read from nuxeo.hyland.cic.discovery.default.agentId."
        + " contextObjectIdsJsonArrayStr is a stringified JSON array of object Ids (doc UUIDs in Nuxeo) to be used for the context."
        + " extraPayloadJsonStr is a stringified JSON Object, to be merged to the payload built by the service (if you need extra parameters)."
        + " You can also pass extra headers in extraHeadersJsonStr as a stringified Json object"
        + " configName is the name of the XML configuration to use (if not passed, using 'default')")
public class HylandKDAskQuestionAndGetAnswerOp {

    public static final String ID = "HylandKnowledgeDiscovery.askQuestionAndGetAnswer";

    @Context
    protected HylandKDService kdService;

    @Param(name = "agentId", required = false)
    protected String agentId;

    @Param(name = "question", required = true)
    protected String question;

    @Param(name = "contextObjectIdsJsonArrayStr", required = false)
    protected String contextObjectIdsJsonArrayStr;

    @Param(name = "extraPayloadJsonStr", required = false)
    protected String extraPayloadJsonStr;

    @Param(name = "extraHeadersJsonStr", required = false)
    protected String extraHeadersJsonStr;

    @Param(name = "configName", required = false)
    protected String configName;

    @OperationMethod
    public Blob run() throws InterruptedException {

        ArrayList<String> contextObjectIds = null;
        if (StringUtils.isNotBlank(contextObjectIdsJsonArrayStr)) {
            JSONArray contextObjectIdsJsonArray = new JSONArray(contextObjectIdsJsonArrayStr);
            contextObjectIds = new ArrayList<>();
            for (int i = 0; i < contextObjectIdsJsonArray.length(); i++) {
                contextObjectIds.add(contextObjectIdsJsonArray.getString(i));
            }
        }

        Map<String, String> extraHeaders = ServicesUtils.jsonObjectStrToMap(extraHeadersJsonStr);

        ServiceCallResult result = kdService.askQuestionAndGetAnswer(configName, agentId, question, contextObjectIds,
                extraPayloadJsonStr, extraHeaders);

        return Blobs.createJSONBlob(result.toJsonString());
    }

}
