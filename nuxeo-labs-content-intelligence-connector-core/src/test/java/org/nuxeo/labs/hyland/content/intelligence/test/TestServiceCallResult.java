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

import org.json.JSONObject;
import org.junit.Test;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;

/**
 * Pure unit tests for {@link ServiceCallResult}. No Nuxeo runtime, no CIC credentials.
 *
 * @since 2025.22
 */
public class TestServiceCallResult {

    /*
     * forceResponseAsJSONObject used to build its JSON by string concatenation. The plain-string branch assigned
     * instead of appending, producing "xxx"} — invalid JSON, so the method threw. It also called
     * new JSONObject(...) on an array, which always throws, and never escaped the raw value.
     */

    @Test
    public void shouldForcePlainStringResponseAsJsonObject() {

        var result = new ServiceCallResult("hello", 200, "OK");
        JSONObject json = result.forceResponseAsJSONObject();
        assertEquals("hello", json.getString("result"));
    }

    @Test
    public void shouldForceStringContainingQuotesAsJsonObject() {

        // Would have produced invalid JSON with the old string concatenation.
        var result = new ServiceCallResult("he said \"hi\"", 200, "OK");
        JSONObject json = result.forceResponseAsJSONObject();
        assertEquals("he said \"hi\"", json.getString("result"));
    }

    @Test
    public void shouldForceJsonArrayResponseAsJsonObject() {

        var result = new ServiceCallResult("[1,2,3]", 200, "OK");
        JSONObject json = result.forceResponseAsJSONObject();
        assertEquals(3, json.getJSONArray("result").length());
    }

    @Test
    public void shouldForceJsonObjectResponseAsJsonObject() {

        var result = new ServiceCallResult("{\"a\":1}", 200, "OK");
        JSONObject json = result.forceResponseAsJSONObject();
        assertEquals(1, json.getJSONObject("result").getInt("a"));
    }

    @Test
    public void shouldForceNullResponseAsJsonObject() {

        var result = new ServiceCallResult(null, 200, "OK");
        JSONObject json = result.forceResponseAsJSONObject();
        assertTrue(json.isNull("result"));
    }

    /*
     * toJsonObject must never throw: since the HTTP layer forwards the body of failed calls, the response may be
     * an HTML error page produced by a gateway rather than the JSON the service normally returns.
     */

    @Test
    public void shouldBuildEnvelopeFromNonJsonResponse() {

        var result = new ServiceCallResult("<html><body>Gateway Timeout</body></html>", 504, "Gateway Timeout");
        JSONObject envelope = result.toJsonObject();

        assertEquals(504, envelope.getInt("responseCode"));
        assertEquals("Gateway Timeout", envelope.getString("responseMessage"));
        assertEquals("<html><body>Gateway Timeout</body></html>", envelope.getString("response"));
    }

    @Test
    public void shouldBuildEnvelopeFromJsonArrayResponse() {

        var result = new ServiceCallResult("[{\"a\":1}]", 200, "OK");
        assertEquals(1, result.toJsonObject().getJSONArray("response").length());
    }

    /*
     * The round-trip constructor used getJSONObject/getJSONArray, so it threw whenever the response was an array
     * or objectKeysMapping was absent — and it is absent from most envelopes, JSONObject.put removing a key whose
     * value is null.
     */

    @Test
    public void shouldRoundTripEnvelopeWithoutObjectKeysMapping() {

        var original = new ServiceCallResult("{\"status\":\"SUCCESS\"}", 200, "OK");
        var restored = new ServiceCallResult(original.toJsonString());

        assertEquals(200, restored.getResponseCode());
        assertEquals("OK", restored.getResponseMessage());
        assertEquals("SUCCESS", restored.getResponseAsJSONObject().getString("status"));
        assertNull(restored.getObjectKeysMapping());
    }

    @Test
    public void shouldRoundTripEnvelopeWithArrayResponse() {

        var original = new ServiceCallResult("[1,2]", 200, "OK");
        var restored = new ServiceCallResult(original.toJsonString());
        assertEquals(2, restored.getResponseAsJSONArray().length());
    }

    /*
     * getResponse strips the surrounding quotes only when the string really has them on both ends. It used to
     * assume that a leading quote implied a trailing one, so a truncated response lost its last character.
     */

    @Test
    public void shouldStripSurroundingQuotesOnlyWhenBalanced() {

        assertEquals("quoted", new ServiceCallResult("\"quoted\"", 200, "OK").getResponse());
        assertEquals("\"truncated", new ServiceCallResult("\"truncated", 200, "OK").getResponse());
        assertEquals("plain", new ServiceCallResult("plain", 200, "OK").getResponse());
    }

}
