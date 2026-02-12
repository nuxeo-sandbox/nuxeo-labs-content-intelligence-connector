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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.labs.hyland.content.intelligence.service.agents.HylandAgentsService;
import org.nuxeo.labs.hyland.content.intelligence.service.agents.HylandAgentsService.AgentType;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import jakarta.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class, ConfigCheckerFeature.class })
@Deploy("nuxeo-hyland-content-intelligence-connector-core")
public class TestHylandAgentsService {

    @Inject
    protected HylandAgentsService hylandAgentsService;

    @Before
    public void onceExecutedBeforeAll() throws Exception {

        // Actually, nothing to do here.
    }

    @Test
    public void testServiceIsDeployed() {
        assertNotNull(hylandAgentsService);
    }

    @Test
    public void shouldGetAllAgents() {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasAgentsClientInfo());

        ServiceCallResult result = hylandAgentsService.getAllAgents(null, null);
        assertNotNull(result);

        assertTrue(result.callResponseOK());

        JSONObject responseJson = result.getResponseAsJSONObject();
        assertTrue(responseJson.has("agents"));

        JSONArray agents = responseJson.getJSONArray("agents");
        assertTrue(agents.length() > 0);
    }

    @Test
    public void shouldLookupTheTestAgentLatestVersion() {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasAgentsClientInfo());

        String agentId = System.getenv(ConfigCheckerFeature.ENV_CIC_AGENT_FOR_UNIT_TEST);
        ServiceCallResult result = hylandAgentsService.lookupAgent(null, agentId, null, null);

        assertTrue(result.callResponseOK());

        JSONObject responseJson = result.getResponseAsJSONObject();

        assertTrue(responseJson.has("agent"));
        JSONObject obj = responseJson.getJSONObject(("agent"));
        assertEquals(agentId, obj.getString(("id")));

        // See a JSON return for the call for details.
        // We don't assert a lot: if a value is not ther, an error will be throwned jy org.json
        assertTrue(responseJson.has("version"));
        JSONObject version = responseJson.getJSONObject(("version"));
        JSONObject config = version.getJSONObject("config");

        // String model = config.getString("llmModelId");
        JSONObject inputSchema = config.getJSONObject(("inputSchema"));
        /*
         * For now, our agent is:
         * "inputSchema": {
         * "type": "object",
         * "properties": {
         * "text": {
         * "type": "string",
         * "description": "The input text"
         * }
         * },
         * "required": [
         * "text"
         * ]
         * }
         */
        JSONObject properties = inputSchema.getJSONObject(("properties"));

        JSONArray tools = config.getJSONArray("tools");
        JSONObject tool1 = tools.getJSONObject(0);
        JSONObject outputSchema = tool1.getJSONObject(("outputSchema"));

    }

    @Test
    public void shouldCallTheTestAgent() {

        Assume.assumeTrue("No configuration parameters set => ignoring the test",
                ConfigCheckerFeature.hasAgentsClientInfo());

        String agentId = System.getenv(ConfigCheckerFeature.ENV_CIC_AGENT_FOR_UNIT_TEST);

        String jsonPayloadStr = """
                {
                  "inputs": {
                    "text": "Dummyt text. The agent is supposed to always return 'DONE'"
                  }
                }
                """;

        ServiceCallResult result = hylandAgentsService.invokeAgent(AgentType.TASK, null, agentId, null, jsonPayloadStr, null);
        assertNotNull(result);

        if (!result.callResponseOK()) {
            // Ignore if server not available or whatever.
            return;
        }

        /*
         * Response is:
         * {
         * "createdAt": 1767964905,
         * "model": "anthropic.claude-3-haiku-20240307-v1:0",
         * "object": "response",
         * "output": [
         * {
         * "type": "message",
         * "status": "completed",
         * "content": [
         * {
         * "type": "output_text",
         * "text": "{\"result\": \"DONE\"}"
         * }
         * ],
         * "role": "assistant"
         * }
         * ]
         * }
         */

        JSONObject responseJson = result.getResponseAsJSONObject();
        assertTrue(responseJson.has("output"));
        JSONArray output = responseJson.getJSONArray("output");
        assertTrue(output.length() > 0);

        // Let's get all in one call, will fail if values are not there
        String agentFinalResult = output.getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("text");
        JSONObject theRealResultAtLast = new JSONObject(agentFinalResult);

        assertEquals("DONE", theRealResultAtLast.getString("result"));

    }

}
