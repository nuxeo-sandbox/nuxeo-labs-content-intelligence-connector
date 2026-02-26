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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.labs.hyland.content.intelligence.automation.agents.HylandAgentsAskKDQuestionViaRagOp;
import org.nuxeo.labs.hyland.content.intelligence.automation.agents.HylandAgentsGetAllAgentsOp;
import org.nuxeo.labs.hyland.content.intelligence.automation.agents.HylandAgentsInvokeTaskOp;
import org.nuxeo.labs.hyland.content.intelligence.service.agents.HylandAgentsService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import javax.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({ AutomationFeature.class, ConfigCheckerFeature.class })
@Deploy("nuxeo-hyland-content-intelligence-connector-core")
public class TestHylandAgentOperations {

    @Inject
    protected CoreSession session;

    @Inject
    protected AutomationService automationService;

    @Inject
    protected HylandAgentsService hylandAgentsService;

    @Test
    public void shouldGetAllAgents() throws Exception {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasAgentsClientInfo());

        OperationContext ctx = new OperationContext(session);

        Map<String, Object> params = new HashMap<>();
        // No params. Using default config.

        Blob result = (Blob) automationService.run(ctx, HylandAgentsGetAllAgentsOp.ID, params);
        Assert.assertNotNull(result);

        JSONObject resultJson = new JSONObject(result.getString());
        // Chekc HTTP call
        int responseCode = resultJson.getInt("responseCode");
        assertEquals(200, responseCode);

        // Now check service results
        JSONObject responseJson = resultJson.getJSONObject("response");
        JSONArray agents = responseJson.getJSONArray("agents");
        assertTrue(agents.length() > 0);
    }

    @Test
    public void shouldInvokeAgent() throws Exception {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasDiscoveryClientInfo());
        
        String agentId = System.getenv(ConfigCheckerFeature.ENV_CIC_AGENT_KD_RAG_UNIT_TEST_AGENT_ID);
        Assume.assumeTrue("No agentId set in env. variables => ignoring the test", StringUtils.isNotBlank(agentId));

        OperationContext ctx = new OperationContext(session);

        Map<String, Object> params = new HashMap<>();        
        params.put("agentId", agentId);
        
        String jsonPayloadStr = """
                {
                  "inputs": {
                    "text": "Dummyt text. The agent is supposed to always return 'DONE'"
                  }
                }
                """;     
        params.put("jsonPayloadStr", jsonPayloadStr);

        Blob result = (Blob) automationService.run(ctx, HylandAgentsInvokeTaskOp.ID, params);
        Assert.assertNotNull(result);

        JSONObject resultJson = new JSONObject(result.getString());
        // Chekc HTTP call
        int responseCode = resultJson.getInt("responseCode");
        assertEquals(200, responseCode);

        // See TestHylandAgentsService#shouldCallTheTestAgent for the JSON format of the response.
        JSONObject responseJson = resultJson.getJSONObject("response");
        assertTrue(responseJson.has("output"));
        JSONArray output = responseJson.getJSONArray("output");
        assertTrue(output.length() > 0);
        
        // Let's get all in one call, will fail if values are not there
        String agentFinalResult = output.getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("text");
        JSONObject theRealResultAtLast = new JSONObject(agentFinalResult);
        
        assertEquals("DONE", theRealResultAtLast.getString("result"));

    }
    
    @Test
    public void shouldInvoqueKDViaRAG() throws Exception {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasDiscoveryClientInfo());
        
        String agentId = System.getenv(ConfigCheckerFeature.ENV_CIC_AGENT_KD_RAG_UNIT_TEST_AGENT_ID);
        if(StringUtils.isBlank(agentId)) {
            System.out.println("Missing the " + ConfigCheckerFeature.ENV_CIC_AGENT_KD_RAG_UNIT_TEST_AGENT_ID + " env. variable => ignoring the test.");
            return;
        }

        OperationContext ctx = new OperationContext(session);

        Map<String, Object> params = new HashMap<>();        
        params.put("agentId", agentId);
        params.put("question", "How many documents do we have in this repository? If your are not totally sure of the response, then return \"I don't know.\".");
        // No context object IDs
        
        Blob result = (Blob) automationService.run(ctx, HylandAgentsAskKDQuestionViaRagOp.ID, params);
        Assert.assertNotNull(result);

        JSONObject resultJson = new JSONObject(result.getString());
        // Chekc HTTP call
        int responseCode = resultJson.getInt("responseCode");
        assertEquals(200, responseCode);

        // See TestHylandAgentsService#shouldCallTheTestAgent for the JSON format of the response.
        JSONObject responseJson = resultJson.getJSONObject("response");
        assertTrue(responseJson.has("output"));
        JSONArray output = responseJson.getJSONArray("output");
        assertTrue(output.length() > 0);
        
        String agentFinalResult = output.getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("text");
        assertNotNull(agentFinalResult);
        
        // Should we assert? This is not a plugin error, it is smeting that changes in the service.
        if(agentFinalResult.toLowerCase().indexOf("i don't know") < 0) {
            System.out.print("Service abnswered, but not what we expected.\nAnswered...\n" + agentFinalResult + "\n...we expected \"I don't know\".");
        }
    }
    
    @Test
    public void shouldInvoqueKDViaRAGWithContext() throws Exception {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasDiscoveryClientInfo());
        
        String agentId = System.getenv(ConfigCheckerFeature.ENV_CIC_AGENT_KD_RAG_UNIT_TEST_AGENT_ID);
        if(StringUtils.isBlank(agentId)) {
            System.out.println("Missing the " + ConfigCheckerFeature.ENV_CIC_AGENT_KD_RAG_UNIT_TEST_AGENT_ID + " env. variable => ignoring the test.");
            return;
        }

        OperationContext ctx = new OperationContext(session);

        Map<String, Object> params = new HashMap<>();        
        params.put("agentId", agentId);
        params.put("question", "How many documents do we have in this repository? If your are not totally sure of the response, then return \"I don't know.\".");
        // Pass non existant objectIDs
        params.put("contextObjectIdsJsonArrayStr", "['123', 'aze', '987']");
        
        Blob result = (Blob) automationService.run(ctx, HylandAgentsAskKDQuestionViaRagOp.ID, params);
        Assert.assertNotNull(result);

        JSONObject resultJson = new JSONObject(result.getString());
        // Chekc HTTP call
        int responseCode = resultJson.getInt("responseCode");
        assertEquals(200, responseCode);

        // See TestHylandAgentsService#shouldCallTheTestAgent for the JSON format of the response.
        JSONObject responseJson = resultJson.getJSONObject("response");
        assertTrue(responseJson.has("output"));
        JSONArray output = responseJson.getJSONArray("output");
        assertTrue(output.length() > 0);
        
        String agentFinalResult = output.getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("text");
        assertNotNull(agentFinalResult);
        
        // Should we assert? This is not a plugin error, it is smeting that changes in the service.
        if(agentFinalResult.toLowerCase().indexOf("empty response") < 0) {
            System.out.print("Service answered, but not what we expected.\nAnswered...\n" + agentFinalResult + "\n...we expected \"Empty Response\".");
        }
    }
    
    @Test
    public void shouldInvoqueKDViaRAGSimplifiedResponse() throws Exception {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasDiscoveryClientInfo());
        
        String agentId = System.getenv(ConfigCheckerFeature.ENV_CIC_AGENT_KD_RAG_UNIT_TEST_AGENT_ID);
        if(StringUtils.isBlank(agentId)) {
            System.out.println("Missing the " + ConfigCheckerFeature.ENV_CIC_AGENT_KD_RAG_UNIT_TEST_AGENT_ID + " env. variable => ignoring the test.");
            return;
        }

        OperationContext ctx = new OperationContext(session);

        Map<String, Object> params = new HashMap<>();        
        params.put("agentId", agentId);
        params.put("question", "How many documents do we have in this repository? If your are not totally sure of the response, then return \"I don't know.\".");
        // No context object IDs
        params.put("returnSimplifiedJson", true);
        
        Blob result = (Blob) automationService.run(ctx, HylandAgentsAskKDQuestionViaRagOp.ID, params);
        Assert.assertNotNull(result);

        JSONObject resultJson = new JSONObject(result.getString());
        // Chekc HTTP call
        int responseCode = resultJson.getInt("responseCode");
        assertEquals(200, responseCode);

        // See HylandAgentsService#simplifyResponse for the JSON format of the response.
        JSONObject responseJson = resultJson.getJSONObject("response");
        assertTrue(responseJson.has("simplifiedProcessingSuccess"));
        assertTrue(responseJson.getBoolean("simplifiedProcessingSuccess"));
        
        assertTrue(responseJson.has("text"));
        String answer = responseJson.getString("text");
        
        assertTrue(responseJson.has("sources"));
        JSONArray sources = responseJson.getJSONArray("sources");
        if(sources.length() > 0) {
            JSONObject oneSource = sources.getJSONObject(0);
            assertTrue(oneSource.has("objectId"));
            assertTrue(oneSource.has("score"));
        }
        
        // Should we assert? This is not a plugin error, it is smeting that changes in the service.
        if(answer.toLowerCase().indexOf("i don't know") < 0) {
            System.out.print("Service abnswered, but not what we expected.\nAnswered...\n" + answer + "\n...we expected \"I don't know\".");
        }

    }

}
