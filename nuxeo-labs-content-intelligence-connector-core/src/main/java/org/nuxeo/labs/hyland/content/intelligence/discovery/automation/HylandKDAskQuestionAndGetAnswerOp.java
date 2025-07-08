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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
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
 * @since TODO
 */
@Operation(id = HylandKDAskQuestionAndGetAnswerOp.ID, category = "Hyland Knowledge Discovery", label = "Ask Quesiton and Get Answer", description = ""
        + "Returns a JSON blob holding  the result of the call. Call its getString() method then JSON.parse()."
        + " Ask a question, with optional parameters:"
        + " agenId => If is empty, it will be read from nuxeo.hyland.cic.discovery.default.agentId."
        + " contextObjectIdsJsonArrayStr is a stringified JSON array of object Ids (doc UUIDs in Nuxeo) to be used for the context."
        + " extraPayloadJsonStr is a stringified JSON Object, to be merged to the payload built by the service (if you need extra parameters)."
        + " You can also pass extra headers in extraHeadersJsonStr as a stringified Json object")
public class HylandKDAskQuestionAndGetAnswerOp {

    public static final String ID = "HylandKnowledgeDiscovery.askQuestionAndGetAnswer";

    @Context
    protected HylandKDService kdService;

    @Param(name = "agentId", required = false)
    protected String agentId;

    @Param(name = "question", required = true)
    protected String question;

    @Param(name = "contextObjectIdsJsonArrayStr", required = true)
    protected String contextObjectIdsJsonArrayStr;

    @Param(name = "extraPayloadJsonStr", required = true)
    protected String extraPayloadJsonStr;

    @Param(name = "extraHeadersJsonStr", required = true)
    protected String extraHeadersJsonStr;

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

        Map<String, String> extraHeaders = null;
        if (StringUtils.isNotBlank(extraHeadersJsonStr)) {
            JSONObject extraHeadersJson = new JSONObject(extraHeadersJsonStr);
            extraHeaders = new HashMap<>();
            Iterator<String> keys = extraHeadersJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                extraHeaders.put(key, extraHeadersJson.getString(key));
            }
        }

        ServiceCallResult result = kdService.askQuestionAndGetAnswer(agentId, question, contextObjectIds,
                extraPayloadJsonStr, extraHeaders);

        return Blobs.createJSONBlob(result.toJsonString());
    }

}
