/*
 * (C) Copyright 2026 Hyland (http://hyland.com/) and others.
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.CICEnrichmentHelperImpl;

/**
 * Pure unit tests for the value-formatting part of {@link CICEnrichmentHelperImpl}, the Java port of the Studio
 * {@code metadataJsonValueToString} algorithm. No Nuxeo runtime, no CIC credentials.
 *
 * @since 2025.22
 */
public class TestCICEnrichmentHelper {

    protected final CICEnrichmentHelperImpl helper = new CICEnrichmentHelperImpl();

    @Test
    public void shouldFormatScalars() {

        assertEquals("hello", helper.metadataJsonValueToString("hello"));
        assertEquals("42", helper.metadataJsonValueToString(42));
        assertEquals("true", helper.metadataJsonValueToString(Boolean.TRUE));
        assertNull(helper.metadataJsonValueToString(null));
        assertNull(helper.metadataJsonValueToString(JSONObject.NULL));
    }

    @Test
    public void shouldFormatArrayOfScalars() {

        assertEquals("", helper.metadataJsonValueToString(new JSONArray()));
        assertEquals("a,b,c", helper.metadataJsonValueToString(new JSONArray("[\"a\",\"b\",\"c\"]")));
    }

    @Test
    public void shouldFormatArrayOfArrays() {

        assertEquals("a,b\nc,d", helper.metadataJsonValueToString(new JSONArray("[[\"a\",\"b\"],[\"c\",\"d\"]]")));
    }

    @Test
    public void shouldFormatArrayOfObjects() {

        String out = helper.metadataJsonValueToString(new JSONArray("[{\"k\":\"v\"},{\"k2\":\"v2\"}]"));
        assertEquals("k: v\nk2: v2", out);
    }

    @Test
    public void shouldFormatObject() {

        assertEquals("k\n  v", helper.metadataJsonValueToString(new JSONObject("{\"k\":\"v\"}")));
    }

    /**
     * The falsy rule inherited from the JS port made {@code 0} and {@code false} disappear entirely: they were
     * converted to the empty string, and {@code objectsArrayToString} skips empty values.
     */
    @Test
    public void shouldKeepZeroAndFalseInsideArrayOfObjects() {

        String out = helper.metadataJsonValueToString(new JSONArray("[{\"count\":0,\"flag\":false}]"));
        assertTrue("0 must not be dropped, got: " + out, out.contains("count: 0"));
        assertTrue("false must not be dropped, got: " + out, out.contains("flag: false"));
    }

    /**
     * Boolean normalisation, per the agreed contract: real booleans and the strings true/yes/false/no become
     * "true"/"false". Numbers are deliberately NOT coerced — turning {@code 1} into {@code "true"} would corrupt
     * an ordinary count.
     */
    @Test
    public void shouldNormalizeBooleanLookingValuesButNotNumbers() {

        String out = helper.metadataJsonValueToString(
                new JSONArray("[{\"a\":\"TRUE\",\"b\":\"Yes\",\"c\":\"no\",\"d\":\"False\",\"e\":1,\"f\":0}]"));

        assertTrue("'TRUE' should normalize, got: " + out, out.contains("a: true"));
        assertTrue("'Yes' should normalize, got: " + out, out.contains("b: true"));
        assertTrue("'no' should normalize, got: " + out, out.contains("c: false"));
        assertTrue("'False' should normalize, got: " + out, out.contains("d: false"));

        assertTrue("1 must stay a number, got: " + out, out.contains("e: 1"));
        assertTrue("0 must stay a number, got: " + out, out.contains("f: 0"));
    }

    @Test
    public void shouldKeepNonBooleanStringsUnchanged() {

        String out = helper.metadataJsonValueToString(new JSONArray("[{\"a\":\"yesterday\",\"b\":\"nothing\"}]"));
        assertTrue(out.contains("a: yesterday"));
        assertTrue(out.contains("b: nothing"));
    }

    /**
     * DELIBERATE behaviour, kept on purpose: a value shaped like an ISO date has its hyphens replaced with a
     * bullet. Inherited from the Studio JS port. This test exists so the behaviour cannot be dropped by accident
     * — and so a reviewer finds it documented rather than flagging it again.
     */
    @Test
    public void shouldReplaceHyphensOfIsoDatesWithBullets() {

        assertEquals("2025\u202201\u202215", helper.metadataJsonValueToString("2025-01-15"));
        // Only a full ISO date matches; anything else is untouched.
        assertEquals("2025-01", helper.metadataJsonValueToString("2025-01"));
        assertEquals("not-a-date", helper.metadataJsonValueToString("not-a-date"));
    }

    @Test
    public void shouldParseEnrichmentResponse() {

        assertNull(helper.parseEnrichmentResponse(null));
        assertNull(helper.parseEnrichmentResponse(""));
        assertNull(helper.parseEnrichmentResponse("not json"));
        assertEquals(1, helper.parseEnrichmentResponse("{\"a\":1}").getInt("a"));
    }

    @Test
    public void shouldExtractActionResult() {

        String envelope = "{\"response\":{\"results\":[{\"textSummary\":{\"result\":\"the summary\"}}]}}";
        JSONObject parsed = helper.parseEnrichmentResponse(envelope);

        assertEquals("the summary", helper.extractActionResult(parsed, "textSummary"));
        assertNull(helper.extractActionResult(parsed, "imageDescription"));
        assertNull(helper.extractActionResult(null, "textSummary"));
        assertNull(helper.extractActionResult(parsed, ""));
    }

}
