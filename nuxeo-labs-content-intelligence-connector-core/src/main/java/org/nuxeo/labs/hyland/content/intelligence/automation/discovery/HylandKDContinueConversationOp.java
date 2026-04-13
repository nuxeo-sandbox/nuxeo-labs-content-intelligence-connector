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
 * Continue an existing conversation by sending a follow-up question. The agent uses previous
 * messages in the conversation as context. Returns the new message (question + answer) synchronously.
 *
 * @since 2025
 */
@Operation(id = HylandKDContinueConversationOp.ID, category = "Hyland Knowledge Discovery", label = "Continue Conversation", description = ""
        + "Continues an existing conversation with a Knowledge Discovery agent by sending a follow-up question."
        + " The agent retains context from previous messages in the conversation."
        + " Returns a JSON blob with the new message (question + answer)."
        + " The result will have a 'responseCode' property that you should check (must be 200),"
        + " and the returned result is in the 'response' property."
        + " agentId => If empty, it is read from nuxeo.hyland.cic.discovery.default.agentId."
        + " conversationId is required and must be obtained from a previous call to HylandKnowledgeDiscovery.startConversation."
        + " dynamicFilterJsonStr is an optional stringified JSON object to narrow the search scope."
        + " You can also pass extra headers in extraHeadersJsonStr as a stringified JSON object."
        + " configName is the name of the XML configuration to use for authentication and baseUrl (if not passed, using 'default')")
public class HylandKDContinueConversationOp {

    public static final String ID = "HylandKnowledgeDiscovery.continueConversation";

    @Context
    protected HylandKDService kdService;

    @Param(name = "agentId", required = false)
    protected String agentId;

    @Param(name = "conversationId", required = true)
    protected String conversationId;

    @Param(name = "question", required = true)
    protected String question;

    @Param(name = "dynamicFilterJsonStr", required = false)
    protected String dynamicFilterJsonStr;

    @Param(name = "extraHeadersJsonStr", required = false)
    protected String extraHeadersJsonStr;

    @Param(name = "configName", required = false)
    protected String configName;

    @OperationMethod
    public Blob run() {

        Map<String, String> extraHeaders = ServicesUtils.jsonObjectStrToMap(extraHeadersJsonStr);

        ServiceCallResult result = kdService.continueConversation(configName, agentId, conversationId,
                question, dynamicFilterJsonStr, extraHeaders);

        return Blobs.createJSONBlob(result.toJsonString());
    }

}
