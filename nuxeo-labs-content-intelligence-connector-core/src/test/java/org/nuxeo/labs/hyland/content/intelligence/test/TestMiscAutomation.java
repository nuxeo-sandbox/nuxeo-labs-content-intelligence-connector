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
 *     Thibaud Arguillere (With the help of Opencode/Claude Opus for the Web UI port from a Studio project)
 */
package org.nuxeo.labs.hyland.content.intelligence.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.HashMap;
import java.util.Map;

import jakarta.inject.Inject;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.labs.hyland.content.intelligence.automation.HylandCIGetContributionNamesOp;
import org.nuxeo.labs.hyland.content.intelligence.automation.enrichment.ConfigureServiceOp;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.HylandKEService;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.HylandKEServiceImpl;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features({ AutomationFeature.class, ConfigCheckerFeature.class })
@Deploy("nuxeo-hyland-content-intelligence-connector-core")
public class TestMiscAutomation {

    @Inject
    protected CoreSession session;

    @Inject
    protected AutomationService automationService;

    @Inject
    protected HylandKEService hylandKEService;

    @Test
    public void shouldChangeConfig() throws Exception {

        HylandKEServiceImpl impl = (HylandKEServiceImpl) hylandKEService;

        OperationContext ctx = new OperationContext(session);

        Map<String, Object> params = new HashMap<>();
        params.put("maxTries", 20);
        params.put("sleepIntervalMS", 5000);
        // No similarMetadat in this test

        automationService.run(ctx, ConfigureServiceOp.ID, params);
        assertEquals(20, impl.getPullResultsMaxTries());
        assertEquals(5000, impl.getPullResultsSleepIntervalMS());


        params.put("maxTries", -1);
        params.put("sleepIntervalMS", -1);
        automationService.run(ctx, ConfigureServiceOp.ID, params);
        assertEquals(20, impl.getPullResultsMaxTries());
        assertEquals(5000, impl.getPullResultsSleepIntervalMS());


        params.put("maxTries", 0);
        params.put("sleepIntervalMS", 0);
        automationService.run(ctx, ConfigureServiceOp.ID, params);
        assertEquals(HylandKEServiceImpl.PULL_RESULTS_MAX_TRIES_DEFAULT, impl.getPullResultsMaxTries());
        assertEquals(HylandKEServiceImpl.PULL_RESULTS_SLEEP_INTERVAL_DEFAULT, impl.getPullResultsSleepIntervalMS());

    }

    /**
     * An omitted parameter must behave exactly like -1, i.e. leave the current value untouched.
     * <p>
     * Until 2025.22 an omitted {@code maxTries} was translated to 1, which silently set pullResultsMaxTries=1 on a
     * static field, i.e. for the whole JVM: enrichment then stopped polling after a single attempt and never
     * returned any result. The pre-existing test always passed both parameters explicitly, so it never exercised
     * this path.
     *
     * @since 2025.22
     */
    @Test
    public void omittedConfigureParamsShouldNotChangeAnything() throws Exception {

        HylandKEServiceImpl impl = (HylandKEServiceImpl) hylandKEService;

        OperationContext ctx = new OperationContext(session);

        Map<String, Object> params = new HashMap<>();
        params.put("maxTries", 20);
        params.put("sleepIntervalMS", 5000);
        automationService.run(ctx, ConfigureServiceOp.ID, params);
        assertEquals(20, impl.getPullResultsMaxTries());
        assertEquals(5000, impl.getPullResultsSleepIntervalMS());

        // Only sleepIntervalMS is passed: maxTries must be left alone.
        Map<String, Object> sleepOnly = new HashMap<>();
        sleepOnly.put("sleepIntervalMS", 1000);
        automationService.run(ctx, ConfigureServiceOp.ID, sleepOnly);
        assertEquals(20, impl.getPullResultsMaxTries());
        assertEquals(1000, impl.getPullResultsSleepIntervalMS());

        // Nothing at all is passed: both must be left alone.
        automationService.run(ctx, ConfigureServiceOp.ID, new HashMap<>());
        assertEquals(20, impl.getPullResultsMaxTries());
        assertEquals(1000, impl.getPullResultsSleepIntervalMS());

        // Restore the defaults so the static state does not leak into the other tests of the shared JVM.
        Map<String, Object> reset = new HashMap<>();
        reset.put("maxTries", 0);
        reset.put("sleepIntervalMS", 0);
        automationService.run(ctx, ConfigureServiceOp.ID, reset);
    }

    @Test
    @Deploy("nuxeo-hyland-content-intelligence-connector-core:more-mock-configs.xml")
    public void shouldGetConfigNames() throws Exception {

        OperationContext ctx = new OperationContext(session);;

        Blob resultBlob = (Blob) automationService.run(ctx, HylandCIGetContributionNamesOp.ID);
        assertNotNull(resultBlob);

        String resultJsonStr = resultBlob.getString();
        JSONObject resultJson = new JSONObject(resultJsonStr);

        JSONArray contribs = resultJson.getJSONArray("knowledgeEnrichment");
        assertEquals(3, contribs.length());
        contribs.toList().contains("default");
        contribs.toList().contains("more-ke-1");
        contribs.toList().contains("more-ke-with-embeddings");

        contribs = resultJson.getJSONArray("dataCuration");
        assertEquals(2, contribs.length());
        contribs.toList().contains("default");
        contribs.toList().contains("more-dc-1");

        contribs = resultJson.getJSONArray("knowledgeDiscovery");
        assertEquals(2, contribs.length());
        contribs.toList().contains("default");
        contribs.toList().contains("more-kd-1");

    }
}
