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
 * Submit feedback (Good, Bad, or Retry) for a specific message in a conversation.
 *
 * @since 2025
 */
@Operation(id = HylandKDConversationFeedbackOp.ID, category = "Hyland Knowledge Discovery", label = "Submit Conversation Feedback", description = ""
        + "Submits feedback for a specific message in a Knowledge Discovery conversation."
        + " The result will have a 'responseCode' property that you should check (must be 200),"
        + " and the returned result is in the 'response' property."
        + " agentId => If empty, it is read from nuxeo.hyland.cic.discovery.default.agentId."
        + " conversationId and messageId are required."
        + " feedback must be one of: 'Good', 'Bad', or 'Retry'."
        + " You can also pass extra headers in extraHeadersJsonStr as a stringified JSON object."
        + " configName is the name of the XML configuration to use for authentication and baseUrl (if not passed, using 'default')")
public class HylandKDConversationFeedbackOp {

    public static final String ID = "HylandKnowledgeDiscovery.conversationFeedback";

    @Context
    protected HylandKDService kdService;

    @Param(name = "agentId", required = false)
    protected String agentId;

    @Param(name = "conversationId", required = true)
    protected String conversationId;

    @Param(name = "messageId", required = true)
    protected String messageId;

    @Param(name = "feedback", required = true)
    protected String feedback;

    @Param(name = "extraHeadersJsonStr", required = false)
    protected String extraHeadersJsonStr;

    @Param(name = "configName", required = false)
    protected String configName;

    @OperationMethod
    public Blob run() {

        Map<String, String> extraHeaders = ServicesUtils.jsonObjectStrToMap(extraHeadersJsonStr);

        ServiceCallResult result = kdService.submitConversationFeedback(configName, agentId, conversationId,
                messageId, feedback, extraHeaders);

        return Blobs.createJSONBlob(result.toJsonString());
    }

}
