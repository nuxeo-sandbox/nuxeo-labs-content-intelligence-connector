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

import javax.inject.Inject;

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
import org.nuxeo.labs.hyland.content.intelligence.discovery.automation.HylandKDInvokeOp;
import org.nuxeo.labs.hyland.content.intelligence.discovery.service.HylandKDService;
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

    @Test
    public void testInvokeGetAllAgents() throws Exception {

        Assume.assumeTrue(ConfigCheckerFeature.hasDiscoveryClientInfo());

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
}
