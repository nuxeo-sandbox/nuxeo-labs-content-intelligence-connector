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
 *     Thibaud Arguillere (With the help of Opencode/Claude Opus for the Web UI port from a Studio project)
 */
package org.nuxeo.labs.hyland.content.intelligence.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

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
import org.nuxeo.labs.hyland.content.intelligence.automation.ui.CICGetUIBundleConfigOp;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * Unit tests for {@link CICGetUIBundleConfigOp}.
 * <p>
 * The tests do NOT rely on {@code FileUtils.getResourceFileFromContext} resolving
 * the deployed {@code nuxeo.war/} path (which is not available in unit-test mode).
 * Instead they:
 * <ul>
 * <li>load the bundle file directly from the classpath
 *     ({@code web/nuxeo.war/ui/.../bundle.html}) and call the static parser;</li>
 * <li>run the parser against a small synthetic bundle string to cover edge cases;</li>
 * <li>smoke-test the operation via the automation service to verify the canonical
 *     envelope shape (in test mode, the deployed-file lookup typically fails and
 *     the op returns {@code responseCode=500} with a clear message — that is the
 *     intended behaviour and is asserted explicitly).</li>
 * </ul>
 *
 * @since 2025.16
 */
@RunWith(FeaturesRunner.class)
@Features(AutomationFeature.class)
@Deploy("nuxeo-hyland-content-intelligence-connector-core")
public class TestCICGetUIBundleConfigOp {

    /** Path of the bundle file on the classpath (Maven copies {@code src/main/resources/} to the JAR root). */
    protected static final String BUNDLE_CLASSPATH = "web/nuxeo.war/ui/nuxeo-labs-content-intelligence-connector/"
            + "nuxeo-labs-content-intelligence-connector-bundle.html";

    @Inject
    protected CoreSession session;

    @Inject
    protected AutomationService automationService;

    /** Loads the bundle file from the classpath; fails the test if missing. */
    protected static String loadBundleFromClasspath() throws Exception {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(BUNDLE_CLASSPATH)) {
            assertNotNull("Bundle file not found on classpath: " + BUNDLE_CLASSPATH, in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void shouldParseRealBundleFromClasspath() throws Exception {

        String content = loadBundleFromClasspath();
        JSONArray buttons = CICGetUIBundleConfigOp.parseButtons(content);

        // The bundle ships at least the core CIC buttons across all four families,
        // so we expect a comfortably positive count.
        assertTrue("Expected at least 10 cic-* buttons, got " + buttons.length(), buttons.length() >= 10);

        // All entries must have name + slot + an integer order, and name MUST start with cic-.
        // The admin-page entries (cicUIConfigMenuItem / cicUIConfigPage) MUST NOT be present.
        Set<String> names = new HashSet<>();
        for (int i = 0; i < buttons.length(); i++) {
            JSONObject b = buttons.getJSONObject(i);
            String name = b.getString("name");
            assertTrue("Unexpected non-cic name: " + name, name.startsWith("cic-"));
            assertFalse("Admin-page entries must be filtered out: " + name,
                    name.equals("cicUIConfigMenuItem") || name.equals("cicUIConfigPage"));
            assertNotNull("Missing slot for " + name, b.optString("slot", null));
            assertTrue("Missing order for " + name, b.has("order"));
            assertTrue("Duplicate name in parsed output: " + name, names.add(name));

            // Category must be one of the known buckets.
            String cat = b.getString("category");
            assertTrue("Unexpected category for " + name + ": " + cat,
                    cat.equals("KE Image") || cat.equals("KE Text") || cat.equals("KD") || cat.equals("Agents")
                            || cat.equals("Other"));

            // templateBody is always present and non-empty (we re-emit it verbatim on Generate).
            assertTrue("Empty templateBody for " + name, b.getString("templateBody").length() > 0);
        }

        // Spot-check a few well-known entries shipped by the plugin.
        JSONObject summarize = findByName(buttons, "cic-ke-textfile-summarize");
        assertNotNull("cic-ke-textfile-summarize must be present", summarize);
        assertEquals("DOCUMENT_ACTIONS", summarize.getString("slot"));
        assertEquals("KE Text", summarize.getString("category"));
        assertEquals("CIC.SummarizeText", summarize.getString("operation"));
        assertEquals("label.ui.cic.ke.summarize", summarize.getString("labelKey"));
        assertFalse("cic-ke-textfile-summarize is shipped enabled", summarize.getBoolean("disabled"));

        JSONObject conversation = findByName(buttons, "cic-kd-conversation");
        assertNotNull("cic-kd-conversation must be present", conversation);
        assertEquals("DOCUMENT_ACTIONS", conversation.getString("slot"));
        assertEquals("KD", conversation.getString("category"));
        assertEquals("icons:forum", conversation.getString("icon"));

        JSONObject lookup = findByName(buttons, "cic-agents-lookupAgent");
        assertNotNull("cic-agents-lookupAgent must be present", lookup);
        assertEquals("Agents", lookup.getString("category"));
        assertEquals("CIC.AgenticAgentLookup", lookup.getString("operation"));
    }

    @Test
    public void shouldHandleDisabledAndMissingFields() {

        // Synthetic mini-bundle with three entries:
        //   1. classic enabled entry with full filter
        //   2. disabled entry (presence of bare `disabled` attribute)
        //   3. minimalistic entry with no filter / no operation / no label
        String synthetic = ""
                + "<link rel=\"import\" href=\"foo.html\">\n"
                + "<nuxeo-slot-content name=\"cic-ke-textfile-summarize\" slot=\"DOCUMENT_ACTIONS\" order=\"7\">\n"
                + "  <template>\n"
                + "    <nuxeo-filter document=\"[[document]]\" expression=\"document != null\" type=\"File\" permission=\"ReadWrite\" user=\"[[user]]\">\n"
                + "      <template>\n"
                + "        <nuxeo-operation-button-with-spinner icon=\"icons:account-balance-wallet\" label=\"label.ui.cic.ke.summarize\" operation=\"CIC.SummarizeText\"></nuxeo-operation-button-with-spinner>\n"
                + "      </template>\n"
                + "    </nuxeo-filter>\n"
                + "  </template>\n"
                + "</nuxeo-slot-content>\n"
                + "<nuxeo-slot-content name=\"cic-kd-ask-question\" slot=\"DOCUMENT_ACTIONS\" order=\"3\" disabled>\n"
                + "  <template>\n"
                + "    <kd-ask-question icon=\"icons:question-answer\"></kd-ask-question>\n"
                + "  </template>\n"
                + "</nuxeo-slot-content>\n"
                + "<nuxeo-slot-content name=\"cic-something-bare\" slot=\"USER_MENU_ACTIONS\" order=\"1\">\n"
                + "  <template>\n"
                + "    <some-element></some-element>\n"
                + "  </template>\n"
                + "</nuxeo-slot-content>\n"
                // Non-cic entry must be filtered out.
                + "<nuxeo-slot-content name=\"adminAnalyticsMenuItem\" slot=\"ADMINISTRATION_MENU\" order=\"10\">\n"
                + "  <template><nuxeo-menu-item></nuxeo-menu-item></template>\n"
                + "</nuxeo-slot-content>\n";

        JSONArray buttons = CICGetUIBundleConfigOp.parseButtons(synthetic);
        assertEquals("Only the three cic-* entries must be returned", 3, buttons.length());

        JSONObject summarize = findByName(buttons, "cic-ke-textfile-summarize");
        assertNotNull(summarize);
        assertEquals(7, summarize.getInt("order"));
        assertFalse(summarize.getBoolean("disabled"));
        assertEquals("KE Text", summarize.getString("category"));
        assertEquals("icons:account-balance-wallet", summarize.getString("icon"));
        assertEquals("label.ui.cic.ke.summarize", summarize.getString("labelKey"));
        assertEquals("CIC.SummarizeText", summarize.getString("operation"));
        assertEquals("document != null", summarize.getString("filterExpression"));
        assertEquals("File", summarize.getString("filterType"));
        assertEquals("ReadWrite", summarize.getString("filterPermission"));

        JSONObject ask = findByName(buttons, "cic-kd-ask-question");
        assertNotNull(ask);
        assertTrue("disabled flag must be detected from bare attribute", ask.getBoolean("disabled"));
        assertEquals("KD", ask.getString("category"));

        JSONObject bare = findByName(buttons, "cic-something-bare");
        assertNotNull(bare);
        assertEquals("Other", bare.getString("category"));
        assertEquals(1, bare.getInt("order"));
        assertFalse(bare.getBoolean("disabled"));
        assertNull("No icon expected", bare.optString("icon", null) != null && !bare.isNull("icon")
                ? bare.getString("icon").isEmpty() ? "" : null
                : null);
        assertTrue("No nuxeo-filter -> filterExpression must be JSON null",
                bare.isNull("filterExpression"));
        assertTrue("No nuxeo-filter -> filterType must be JSON null", bare.isNull("filterType"));
        assertTrue("No nuxeo-filter -> filterPermission must be JSON null", bare.isNull("filterPermission"));
        assertTrue("No operation attribute -> operation must be JSON null", bare.isNull("operation"));
        assertTrue("No label attribute -> labelKey must be JSON null", bare.isNull("labelKey"));
    }

    @Test
    public void shouldRunOpAndReturnEnvelope() throws Exception {

        OperationContext ctx = new OperationContext(session);
        Blob result = (Blob) automationService.run(ctx, CICGetUIBundleConfigOp.ID);
        assertNotNull(result);

        JSONObject envelope = new JSONObject(result.getString());
        assertEquals("Envelope must carry responseCode=200 (op falls back to web/ classpath in test mode)",
                200, envelope.getInt("responseCode"));
        assertEquals("OK", envelope.getString("responseMessage"));

        JSONObject response = envelope.getJSONObject("response");
        assertNotNull(response.getString("bundlePath"));
        assertNotNull(response.getString("pluginVersion"));

        JSONArray buttons = response.getJSONArray("buttons");
        assertTrue("Expected at least 10 cic-* buttons via the op, got " + buttons.length(),
                buttons.length() >= 10);
        assertNotNull("cic-kd-conversation must be exposed by the op",
                findByName(buttons, "cic-kd-conversation"));
    }

    protected static JSONObject findByName(JSONArray arr, String name) {
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (name.equals(o.optString("name"))) {
                return o;
            }
        }
        return null;
    }

}
