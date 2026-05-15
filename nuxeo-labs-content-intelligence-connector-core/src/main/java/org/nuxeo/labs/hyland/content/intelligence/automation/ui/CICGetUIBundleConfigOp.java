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
 *     Thibaud Arguillere (With the help of Opencode/Claude Opus for the Web UI port from a Studio project)
 */
package org.nuxeo.labs.hyland.content.intelligence.automation.ui;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.NuxeoException;

/**
 * Returns the list of CIC slot-content buttons declared in this plugin's Web UI bundle file
 * ({@code nuxeo.war/ui/nuxeo-labs-content-intelligence-connector/nuxeo-labs-content-intelligence-connector-bundle.html}),
 * with their state and order as shipped by the plugin.
 * <p>
 * This is a presales/admin helper used by the {@code <cic-ui-config-page>} drawer page.
 * It intentionally reads ONLY this plugin's own bundle: it does NOT attempt to merge with
 * Studio overrides or third-party plugin contributions. The page that calls this op displays
 * a warning banner explaining that the runtime UI may differ from what is shown.
 * <p>
 * Output is a JSON {@link Blob} with the canonical envelope:
 * 
 * <pre>
 * {
 *   "responseCode": 200,
 *   "responseMessage": "OK",
 *   "response": {
 *     "bundlePath":    "nuxeo.war/ui/.../nuxeo-labs-content-intelligence-connector-bundle.html",
 *     "pluginVersion": "2025.16.0-SNAPSHOT",
 *     "buttons": [ { name, category, slot, order, disabled, icon, labelKey, operation,
 *                    filterExpression, filterType, filterPermission, templateBody }, ... ]
 *   }
 * }
 * </pre>
 *
 * @since 2025.16
 */
@Operation(id = CICGetUIBundleConfigOp.ID, category = "Hyland Content Intelligence", label = "CIC: Get UI Bundle Config", description = ""
        + "Reads the plugin's own Web UI bundle file and returns the list of CIC slot-content buttons it declares,"
        + " with their state (slot, order, disabled) and metadata (icon, label key, operation, filter expression, full template body)."
        + " Used by the cic-ui-config-page admin drawer page. Does NOT merge with Studio or third-party overrides.")
public class CICGetUIBundleConfigOp {

    private static final Logger log = LogManager.getLogger(CICGetUIBundleConfigOp.class);

    public static final String ID = "CIC.GetUIBundleConfig";

    protected static final String BUNDLE_REL_PATH = "nuxeo.war" + File.separator + "ui" + File.separator
            + "nuxeo-labs-content-intelligence-connector" + File.separator
            + "nuxeo-labs-content-intelligence-connector-bundle.html";

    /** Reported in the JSON output when {@code pom.properties} cannot be read. */
    protected static final String UNKNOWN_VERSION = "unknown";

    // Matches one top-level <nuxeo-slot-content ...>...</nuxeo-slot-content> block.
    // Non-greedy body capture; assumes no nested <nuxeo-slot-content> (true in our bundle).
    protected static final Pattern SLOT_CONTENT_PATTERN = Pattern.compile(
            "<nuxeo-slot-content\\b([^>]*)>(.*?)</nuxeo-slot-content>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    protected static final Pattern ATTR_NAME = attr("name");

    protected static final Pattern ATTR_SLOT = attr("slot");

    protected static final Pattern ATTR_ORDER = attr("order");

    protected static final Pattern ATTR_DISABLED = Pattern.compile("\\bdisabled\\b", Pattern.CASE_INSENSITIVE);

    protected static final Pattern ATTR_ICON = attr("icon");

    protected static final Pattern ATTR_LABEL = attr("label");

    protected static final Pattern ATTR_OPERATION = attr("operation");

    protected static final Pattern NUXEO_FILTER_OPEN = Pattern.compile("<nuxeo-filter\\b([^>]*)>",
            Pattern.CASE_INSENSITIVE);

    protected static final Pattern ATTR_EXPRESSION = attr("expression");

    protected static final Pattern ATTR_TYPE = attr("type");

    protected static final Pattern ATTR_PERMISSION = attr("permission");

    protected static Pattern attr(String name) {
        return Pattern.compile("\\b" + name + "\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    }

    @OperationMethod
    public Blob run() {

        var envelope = new JSONObject();

        try {
            File bundle = FileUtils.getResourceFileFromContext(BUNDLE_REL_PATH);
            if (bundle == null || !bundle.isFile()) {
                envelope.put("responseCode", 500);
                envelope.put("responseMessage",
                        "Plugin bundle file not found at: " + BUNDLE_REL_PATH);
                return Blobs.createJSONBlob(envelope.toString());
            }

            String content = Files.readString(bundle.toPath(), StandardCharsets.UTF_8);
            String pluginVersion = readPluginVersion();

            JSONArray buttons = parseButtons(content);

            var response = new JSONObject();
            response.put("bundlePath", BUNDLE_REL_PATH.replace(File.separatorChar, '/'));
            response.put("pluginVersion", pluginVersion);
            response.put("buttons", buttons);

            envelope.put("responseCode", 200);
            envelope.put("responseMessage", "OK");
            envelope.put("response", response);
            return Blobs.createJSONBlob(envelope.toString());

        } catch (IOException e) {
            log.error("Failed to read plugin bundle file", e);
            throw new NuxeoException("Failed to read plugin bundle file: " + e.getMessage(), e);
        }
    }

    /**
     * Reads the Maven version from {@code META-INF/maven/.../pom.properties} packaged in the JAR.
     * Returns {@link #UNKNOWN_VERSION} if the file cannot be read.
     */
    protected String readPluginVersion() {
        String resource = "META-INF/maven/org.nuxeo.labs.hyland.content.intelligence/"
                + "nuxeo-labs-content-intelligence-connector-core/pom.properties";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                return UNKNOWN_VERSION;
            }
            var props = new Properties();
            props.load(in);
            return props.getProperty("version", UNKNOWN_VERSION);
        } catch (IOException e) {
            log.warn("Could not read plugin version from {}", resource, e);
            return UNKNOWN_VERSION;
        }
    }

    /**
     * Parses every top-level {@code <nuxeo-slot-content>} block whose {@code name} attribute starts
     * with {@code cic-} (skipping the admin-page wiring entries themselves), and returns a JSON array
     * of button descriptors.
     * <p>
     * Exposed as {@code public static} for unit tests.
     */
    public static JSONArray parseButtons(String content) {
        var buttons = new JSONArray();
        Matcher m = SLOT_CONTENT_PATTERN.matcher(content);
        while (m.find()) {
            String openAttrs = m.group(1);
            String body = m.group(2);

            String name = firstGroup(ATTR_NAME, openAttrs);
            if (name == null || !name.startsWith("cic-")) {
                // Skip non-CIC entries (the admin page's own menu/page entries are NOT cic-* prefixed).
                continue;
            }

            var entry = new JSONObject();
            entry.put("name", name);
            entry.put("category", categoryOf(name));
            entry.put("slot", nullSafe(firstGroup(ATTR_SLOT, openAttrs)));
            entry.put("order", parseInt(firstGroup(ATTR_ORDER, openAttrs), 0));
            entry.put("disabled", ATTR_DISABLED.matcher(openAttrs).find());

            entry.put("icon", nullSafe(firstGroup(ATTR_ICON, body)));
            entry.put("labelKey", nullSafe(firstGroup(ATTR_LABEL, body)));
            entry.put("operation", nullSafe(firstGroup(ATTR_OPERATION, body)));

            // Pull the FIRST <nuxeo-filter ...> opening tag's attributes (if any).
            Matcher fm = NUXEO_FILTER_OPEN.matcher(body);
            if (fm.find()) {
                String filterAttrs = fm.group(1);
                entry.put("filterExpression", nullSafe(firstGroup(ATTR_EXPRESSION, filterAttrs)));
                entry.put("filterType", nullSafe(firstGroup(ATTR_TYPE, filterAttrs)));
                entry.put("filterPermission", nullSafe(firstGroup(ATTR_PERMISSION, filterAttrs)));
            } else {
                entry.put("filterExpression", JSONObject.NULL);
                entry.put("filterType", JSONObject.NULL);
                entry.put("filterPermission", JSONObject.NULL);
            }

            // Re-emit the body verbatim so the page can produce a complete override snippet.
            entry.put("templateBody", body);

            buttons.put(entry);
        }
        return buttons;
    }

    protected static String firstGroup(Pattern p, String input) {
        if (input == null) {
            return null;
        }
        Matcher m = p.matcher(input);
        return m.find() ? m.group(1) : null;
    }

    protected static Object nullSafe(String s) {
        return s == null ? JSONObject.NULL : s;
    }

    protected static int parseInt(String s, int fallback) {
        if (s == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    protected static String categoryOf(String name) {
        if (name.startsWith("cic-ke-image-")) {
            return "KE Image";
        }
        if (name.startsWith("cic-ke-textfile-") || name.startsWith("cic-ke-text-")) {
            return "KE Text";
        }
        if (name.startsWith("cic-kd-")) {
            return "KD";
        }
        if (name.startsWith("cic-agents-")) {
            return "Agents";
        }
        return "Other";
    }

    /** Visible for {@link List} assertions in unit tests. */
    protected static List<String> categoriesOrder() {
        return new ArrayList<>(List.of("KE Image", "KE Text", "KD", "Agents", "Other"));
    }

}
