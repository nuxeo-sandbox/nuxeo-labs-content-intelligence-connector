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
package org.nuxeo.labs.hyland.content.intelligence.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.labs.hyland.content.intelligence.service.discovery.HylandKDService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class, ConfigCheckerFeature.class })
@Deploy("nuxeo-hyland-content-intelligence-connector-core")
public class TestHylandKDService {

    @Inject
    protected HylandKDService hylandKDService;

    @Before
    public void onceExecutedBeforeAll() throws Exception {

        // Actually, nothing to do here.
    }

    @Test
    public void testServiceIsDeployed() {
        assertNotNull(hylandKDService);
    }

    @Test
    public void shouldGetAllAgentsWithLowLevelCall() {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasDiscoveryClientInfo());

        ServiceCallResult result = hylandKDService.invokeDiscovery(null, "GET", "/agent/agents", null);
        assertNotNull(result);

        assertTrue(result.callResponseOK());

        JSONArray arr = result.getResponseAsJSONArray();
        assertNotNull(arr);
        assertTrue(arr.length() > 0);
    }

    @Test
    public void shouldGetAllAgentsWithHelper() {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasDiscoveryClientInfo());

        ServiceCallResult result = hylandKDService.getAllAgents(null, null);
        assertNotNull(result);

        assertTrue(result.callResponseOK());

        JSONArray arr = result.getResponseAsJSONArray();
        assertNotNull(arr);
        assertTrue(arr.length() > 0);
    }

    @Test
    public void shouldAskQuestionAndGetAnswerWithHelper() throws Exception {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasDiscoveryClientInfo());

        String agentId = System.getenv(ConfigCheckerFeature.ENV_CIC_DISCOVERY_UNIT_TEST_AGENT_ID);
        ServiceCallResult result = hylandKDService.askQuestionAndGetAnswer(null, agentId,
                "How many documents do we have in this repository?", null, null, null);
        assertNotNull(result);

        // We can't consider a 404 as an error here, it's the service not responding in time.
        if (result.callResponseOK()) {
            JSONObject response = result.getResponseAsJSONObject();
            String answer = response.getString("answer");
            assertFalse(StringUtils.isBlank(answer));

            String answerAgentId = response.getString("agentId");
            assertEquals(agentId, answerAgentId);
        }

    }

    @Test
    public void shouldAskQuestionAndGetAnswerWithLowLevelCall() throws Exception {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasDiscoveryClientInfo());

        // 1. Get an agent (we use any agent here)
        String agentId = System.getenv(ConfigCheckerFeature.ENV_CIC_DISCOVERY_UNIT_TEST_AGENT_ID);
        Assume.assumeTrue(StringUtils.isNotBlank(agentId));
        /*
         * ServiceCallResult result = hylandKDService.invokeDiscovery("GET", "/agent/agents", null);
         * assertNotNull(result);
         * assertTrue(result.callResponseOK());
         * JSONArray arr = result.getResponseAsJSONArray();
         * String agentId = arr.getJSONObject(0).getString("id");
         */

        // 2. Call service with this agent
        String endPoint = "/agent/agents/" + agentId + "/questions";
        JSONObject payload = new JSONObject();
        payload.put("question", "How many documents do we have in this repository?");
        payload.put("contextObjectIds", new JSONArray());

        ServiceCallResult result = hylandKDService.invokeDiscovery(null, "POST", endPoint, payload.toString());
        assertNotNull(result);
        assertEquals(202, result.getResponseCode()); // ACCEPTED

        JSONObject response = result.getResponseAsJSONObject();
        String questionId = response.getString("questionId");
        assertFalse(StringUtils.isBlank(questionId));

        // Try to get an an answer. A time out is not an error, just an "ignore"
        endPoint = "/qna/questions/" + questionId + "/answer";
        int count = 0;
        int MAXTRIES = 10;
        boolean gotIt = false;
        do {
            count += 1;
            if (count > 2) {
                System.out.println("shouldAskQuestionAndGetAnswer, trying to get an answer to question ID " + questionId
                        + ", call " + count + "/" + MAXTRIES + ".");
            }

            result = hylandKDService.invokeDiscovery(null, "GET", endPoint, null);
            assertNotNull(result);
            if (!result.callResponseOK()) {
                Thread.sleep(3000);
            } else {
                response = result.getResponseAsJSONObject();
                // Wheh asked to quickly, we can get a 200 OK with a null answer.
                // In this case, response.getString("answer") throws an error
                String answer = response.optString("answer", null);
                gotIt = StringUtils.isNoneBlank(answer);
                if (!gotIt) {
                    Thread.sleep(3000);
                }
            }
        } while (!gotIt && count < MAXTRIES);

        if (result.callResponseOK()) {
            response = result.getResponseAsJSONObject();
            String answer = response.getString("answer");
            assertFalse(StringUtils.isBlank(answer));

            // System.out.println("\n\n" + answer + "\n\n");

            String answerAgentId = response.getString("agentId");
            assertEquals(agentId, answerAgentId);
        } else {
            System.out.println("\n\n" + result.toJsonString(2));
        }

    }

    // ======================================================================
    // Conversation API tests
    // ======================================================================
    @Test
    public void shouldStartConversation() {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasDiscoveryClientInfo());

        String agentId = System.getenv(ConfigCheckerFeature.ENV_CIC_DISCOVERY_UNIT_TEST_AGENT_ID);
        Assume.assumeTrue("Missing agent ID env variable", StringUtils.isNotBlank(agentId));

        ServiceCallResult result = hylandKDService.startConversation(null, agentId,
                "What types of documents are available?", null, null);
        assertNotNull(result);

        if (result.callResponseOK()) {
            JSONObject response = result.getResponseAsJSONObject();

            // Check conversation metadata
            JSONObject conversation = response.getJSONObject("conversation");
            assertNotNull(conversation);
            String conversationId = conversation.getString("id");
            assertFalse(StringUtils.isBlank(conversationId));

            // Check first message
            JSONObject message = response.getJSONObject("message");
            assertNotNull(message);
            String messageId = message.getString("id");
            assertFalse(StringUtils.isBlank(messageId));
            String answer = message.getString("answer");
            assertFalse(StringUtils.isBlank(answer));
            assertEquals("Answered", message.getString("status"));
        } else {
            System.out.println("shouldStartConversation - unexpected response:\n" + result.toJsonString(2));
        }
    }

    @Test
    public void shouldContinueConversation() {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasDiscoveryClientInfo());

        String agentId = System.getenv(ConfigCheckerFeature.ENV_CIC_DISCOVERY_UNIT_TEST_AGENT_ID);
        Assume.assumeTrue("Missing agent ID env variable", StringUtils.isNotBlank(agentId));

        // 1. Start a conversation
        ServiceCallResult result = hylandKDService.startConversation(null, agentId,
                "What types of documents are available?", null, null);
        assertNotNull(result);
        Assume.assumeTrue("Could not start conversation", result.callResponseOK());

        JSONObject response = result.getResponseAsJSONObject();
        String conversationId = response.getJSONObject("conversation").getString("id");
        String firstMessageId = response.getJSONObject("message").getString("id");

        // 2. Continue the conversation with a follow-up question
        result = hylandKDService.continueConversation(null, agentId, conversationId,
                "Can you tell me more about the first type?", null, null);
        assertNotNull(result);

        if (result.callResponseOK()) {
            JSONObject followUpResponse = result.getResponseAsJSONObject();
            String followUpMessageId = followUpResponse.getString("id");
            assertFalse(StringUtils.isBlank(followUpMessageId));
            // The follow-up message should have a different ID
            assertFalse(firstMessageId.equals(followUpMessageId));

            String answer = followUpResponse.getString("answer");
            assertFalse(StringUtils.isBlank(answer));
            assertEquals("Answered", followUpResponse.getString("status"));
        } else {
            System.out.println("shouldContinueConversation - unexpected response:\n" + result.toJsonString(2));
        }
    }

    @Test
    public void shouldSubmitConversationFeedback() {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasDiscoveryClientInfo());

        String agentId = System.getenv(ConfigCheckerFeature.ENV_CIC_DISCOVERY_UNIT_TEST_AGENT_ID);
        Assume.assumeTrue("Missing agent ID env variable", StringUtils.isNotBlank(agentId));

        // 1. Start a conversation to get a conversationId and messageId
        ServiceCallResult result = hylandKDService.startConversation(null, agentId,
                "What types of documents are available?", null, null);
        assertNotNull(result);
        Assume.assumeTrue("Could not start conversation", result.callResponseOK());

        JSONObject response = result.getResponseAsJSONObject();
        String conversationId = response.getJSONObject("conversation").getString("id");
        String messageId = response.getJSONObject("message").getString("id");

        // 2. Submit feedback on the first message
        result = hylandKDService.submitConversationFeedback(null, agentId, conversationId,
                messageId, "Good", null);
        assertNotNull(result);
        assertTrue("Feedback submission failed with code " + result.getResponseCode(),
                result.callWasSuccesful());
    }

}
