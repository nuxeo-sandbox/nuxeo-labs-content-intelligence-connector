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
 *     Michael Vachette
 *     Thibaud Arguillere
 */
package org.nuxeo.labs.hyland.content.intelligence.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.labs.hyland.content.intelligence.automation.discovery.HylandKDAskQuestionAndGetAnswerOp;
import org.nuxeo.labs.hyland.content.intelligence.automation.discovery.HylandKDGetAllAgentsOp;
import org.nuxeo.labs.hyland.content.intelligence.automation.discovery.HylandKDInvokeOp;
import org.nuxeo.labs.hyland.content.intelligence.automation.discovery.HylandKDStartConversationOp;
import org.nuxeo.labs.hyland.content.intelligence.automation.discovery.HylandKDContinueConversationOp;
import org.nuxeo.labs.hyland.content.intelligence.automation.discovery.HylandKDConversationFeedbackOp;
import org.nuxeo.labs.hyland.content.intelligence.service.discovery.HylandKDService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features({ AutomationFeature.class, ConfigCheckerFeature.class })
@Deploy("nuxeo-hyland-content-intelligence-connector-core")
public class TestHylandKDOperations {

    @Inject
    protected CoreSession session;

    @Inject
    protected AutomationService automationService;

    @Inject
    protected HylandKDService hylandKDService;

    @Ignore
    @Test
    public void testInvokeGetAllAgents() throws Exception {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasDiscoveryClientInfo());

        OperationContext ctx = new OperationContext(session);

        Map<String, Object> params = new HashMap<>();
        params.put("httpMethod", "GET");
        params.put("endpoint", "/agent/agents");
        // No jsonPayload in this test

        Blob result = (Blob) automationService.run(ctx, HylandKDInvokeOp.ID, params);
        Assert.assertNotNull(result);

        JSONObject resultJson = new JSONObject(result.getString());
        // Chekc HTTP call
        int responseCode = resultJson.getInt("responseCode");
        assertEquals(200, responseCode);

        // Now check service results
        // For agent/agents, we receive an array
        JSONArray responseArr = resultJson.getJSONArray("response");
        assertNotNull(responseArr);
        assertTrue(responseArr.length() > 0);
    }

    @Ignore
    @Test
    public void shouldGetAllAgents() throws Exception {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasDiscoveryClientInfo());

        OperationContext ctx = new OperationContext(session);

        Blob result = (Blob) automationService.run(ctx, HylandKDGetAllAgentsOp.ID);
        Assert.assertNotNull(result);

        JSONObject resultJson = new JSONObject(result.getString());
        // Chekc HTTP call
        int responseCode = resultJson.getInt("responseCode");
        assertEquals(200, responseCode);

        // Now check service results
        // For agent/agents, we receive an array
        JSONArray responseArr = resultJson.getJSONArray("response");
        assertNotNull(responseArr);
        assertTrue(responseArr.length() > 0);

    }

    @Test
    public void shouldAskQuestionAndgetAnswer() throws Exception {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasDiscoveryClientInfo());
        
        String agentId = System.getenv(ConfigCheckerFeature.ENV_CIC_DISCOVERY_UNIT_TEST_AGENT_ID);
        if(StringUtils.isBlank(agentId)) {
            System.out.println("Missing the " + ConfigCheckerFeature.ENV_CIC_DISCOVERY_UNIT_TEST_AGENT_ID + " env. variable => ignoring the test.");
            return;
        }

        OperationContext ctx = new OperationContext(session);

        Map<String, Object> params = new HashMap<>();
        params.put("agentId", agentId);
        params.put("question", "How many documents do we have in this repository?");
        // No contextObjectIds, nor jsonPayload nor extra headers in this test

        Blob result = (Blob) automationService.run(ctx, HylandKDAskQuestionAndGetAnswerOp.ID, params);
        Assert.assertNotNull(result);
        
        JSONObject resultJson = new JSONObject(result.getString());
        
        int responseCode = resultJson.getInt("responseCode");
        // We can't consider a 404 as an error here, it's the service not responding in time.
        if (responseCode == 200) {
            JSONObject response = resultJson.getJSONObject("response");
            String answer = response.getString("answer");
            assertFalse(StringUtils.isBlank(answer));

            String answerAgentId = response.getString("agentId");
            assertEquals(agentId, answerAgentId);
        }

    }

    // ======================================================================
    // Conversation API operation tests
    // ======================================================================
    @Test
    public void shouldStartConversationOp() throws Exception {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasDiscoveryClientInfo());

        String agentId = System.getenv(ConfigCheckerFeature.ENV_CIC_DISCOVERY_UNIT_TEST_AGENT_ID);
        if (StringUtils.isBlank(agentId)) {
            System.out.println("Missing the " + ConfigCheckerFeature.ENV_CIC_DISCOVERY_UNIT_TEST_AGENT_ID
                    + " env. variable => ignoring the test.");
            return;
        }

        OperationContext ctx = new OperationContext(session);

        Map<String, Object> params = new HashMap<>();
        params.put("agentId", agentId);
        params.put("question", "What types of documents are available?");

        Blob result = (Blob) automationService.run(ctx, HylandKDStartConversationOp.ID, params);
        Assert.assertNotNull(result);

        JSONObject resultJson = new JSONObject(result.getString());

        int responseCode = resultJson.getInt("responseCode");
        if (responseCode == 200) {
            JSONObject response = resultJson.getJSONObject("response");

            // Check conversation metadata
            JSONObject conversation = response.getJSONObject("conversation");
            assertNotNull(conversation);
            String conversationId = conversation.getString("id");
            assertFalse(StringUtils.isBlank(conversationId));

            // Check first message
            JSONObject message = response.getJSONObject("message");
            assertNotNull(message);
            assertFalse(StringUtils.isBlank(message.getString("id")));
            assertFalse(StringUtils.isBlank(message.getString("answer")));
            assertEquals("Answered", message.getString("status"));
        }
    }

    @Test
    public void shouldContinueConversationOp() throws Exception {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasDiscoveryClientInfo());

        String agentId = System.getenv(ConfigCheckerFeature.ENV_CIC_DISCOVERY_UNIT_TEST_AGENT_ID);
        if (StringUtils.isBlank(agentId)) {
            System.out.println("Missing the " + ConfigCheckerFeature.ENV_CIC_DISCOVERY_UNIT_TEST_AGENT_ID
                    + " env. variable => ignoring the test.");
            return;
        }

        OperationContext ctx = new OperationContext(session);

        // 1. Start a conversation
        Map<String, Object> params = new HashMap<>();
        params.put("agentId", agentId);
        params.put("question", "What types of documents are available?");

        Blob result = (Blob) automationService.run(ctx, HylandKDStartConversationOp.ID, params);
        Assert.assertNotNull(result);

        JSONObject resultJson = new JSONObject(result.getString());
        Assume.assumeTrue("Could not start conversation", resultJson.getInt("responseCode") == 200);

        JSONObject response = resultJson.getJSONObject("response");
        String conversationId = response.getJSONObject("conversation").getString("id");
        String firstMessageId = response.getJSONObject("message").getString("id");

        // 2. Continue the conversation
        ctx = new OperationContext(session);
        params = new HashMap<>();
        params.put("agentId", agentId);
        params.put("conversationId", conversationId);
        params.put("question", "Can you tell me more about the first type?");

        result = (Blob) automationService.run(ctx, HylandKDContinueConversationOp.ID, params);
        Assert.assertNotNull(result);

        resultJson = new JSONObject(result.getString());
        int responseCode = resultJson.getInt("responseCode");
        if (responseCode == 200) {
            response = resultJson.getJSONObject("response");
            String followUpMessageId = response.getString("id");
            assertFalse(StringUtils.isBlank(followUpMessageId));
            assertFalse(firstMessageId.equals(followUpMessageId));
            assertFalse(StringUtils.isBlank(response.getString("answer")));
            assertEquals("Answered", response.getString("status"));
        }
    }

    @Test
    public void shouldSubmitConversationFeedbackOp() throws Exception {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasDiscoveryClientInfo());

        String agentId = System.getenv(ConfigCheckerFeature.ENV_CIC_DISCOVERY_UNIT_TEST_AGENT_ID);
        if (StringUtils.isBlank(agentId)) {
            System.out.println("Missing the " + ConfigCheckerFeature.ENV_CIC_DISCOVERY_UNIT_TEST_AGENT_ID
                    + " env. variable => ignoring the test.");
            return;
        }

        OperationContext ctx = new OperationContext(session);

        // 1. Start a conversation to get conversationId and messageId
        Map<String, Object> params = new HashMap<>();
        params.put("agentId", agentId);
        params.put("question", "What types of documents are available?");

        Blob result = (Blob) automationService.run(ctx, HylandKDStartConversationOp.ID, params);
        Assert.assertNotNull(result);

        JSONObject resultJson = new JSONObject(result.getString());
        Assume.assumeTrue("Could not start conversation", resultJson.getInt("responseCode") == 200);

        JSONObject response = resultJson.getJSONObject("response");
        String conversationId = response.getJSONObject("conversation").getString("id");
        String messageId = response.getJSONObject("message").getString("id");

        // 2. Submit feedback
        ctx = new OperationContext(session);
        params = new HashMap<>();
        params.put("agentId", agentId);
        params.put("conversationId", conversationId);
        params.put("messageId", messageId);
        params.put("feedback", "Good");

        result = (Blob) automationService.run(ctx, HylandKDConversationFeedbackOp.ID, params);
        Assert.assertNotNull(result);

        resultJson = new JSONObject(result.getString());
        int responseCode = resultJson.getInt("responseCode");
        assertTrue("Feedback submission failed with code " + responseCode,
                responseCode >= 200 && responseCode < 300);
    }
}
