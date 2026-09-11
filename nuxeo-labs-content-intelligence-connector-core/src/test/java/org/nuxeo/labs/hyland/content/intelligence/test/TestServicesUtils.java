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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.labs.hyland.content.intelligence.service.ServicesUtils;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

/**
 * Tests for {@link ServicesUtils}. Runs fully offline, no CIC credentials.
 * <p>
 * {@link RuntimeFeature} is needed only by the configuration-parameter tests: {@code Framework.getProperty}
 * throws "Runtime not initialized" outside a Nuxeo runtime. The encoding helpers are pure functions.
 *
 * @since 2025.22
 */
@RunWith(FeaturesRunner.class)
@Features(RuntimeFeature.class)
public class TestServicesUtils {

    @Test
    public void shouldEncodePathSegment() {

        // Unreserved characters must be left alone, in particular the "__" separator of the Content Lake doc id.
        assertEquals("abc-123_def.ghi", ServicesUtils.encodePathSegment("abc-123_def.ghi"));
        assertEquals("source__1234-5678", ServicesUtils.encodePathSegment("source__1234-5678"));

        // Characters that would change the structure of the URL.
        assertEquals("a%2Fb", ServicesUtils.encodePathSegment("a/b"));
        assertEquals("..%2F..%2Fadmin", ServicesUtils.encodePathSegment("../../admin"));
        assertEquals("a%3Fb", ServicesUtils.encodePathSegment("a?b"));
        assertEquals("a%23b", ServicesUtils.encodePathSegment("a#b"));

        // A space must be %20 in a path segment, never "+".
        assertEquals("a%20b", ServicesUtils.encodePathSegment("a b"));
        assertFalse(ServicesUtils.encodePathSegment("a b").contains("+"));
    }

    @Test
    public void shouldEncodeQueryParam() {

        assertEquals("abc-123", ServicesUtils.encodeQueryParam("abc-123"));

        // A raw "&" would inject an extra query parameter.
        assertEquals("a%26admin%3Dtrue", ServicesUtils.encodeQueryParam("a&admin=true"));

        /*
         * The real-world case behind this helper: the mime type was encoded by hand with a single
         * replace("/", "%2F"), so "image/svg+xml" reached the service as "image/svg xml" because the raw "+" is
         * decoded as a space.
         */
        assertEquals("image%2Fsvg%2Bxml", ServicesUtils.encodeQueryParam("image/svg+xml"));
        assertEquals("application%2Fpdf", ServicesUtils.encodeQueryParam("application/pdf"));
    }

    @Test
    public void shouldLeaveBlankValuesUnchanged() {

        assertNull(ServicesUtils.encodePathSegment(null));
        assertNull(ServicesUtils.encodeQueryParam(null));
        assertEquals("", ServicesUtils.encodePathSegment(""));
        assertEquals("", ServicesUtils.encodeQueryParam(""));
    }

    /**
     * {@code configParamToBoolean} used to delegate to {@code Boolean.parseBoolean}, which maps every
     * unrecognised string to {@code false}. So {@code nuxeo.hyland.cic.moreLogs=yes} silently disabled the very
     * traces it was meant to turn on, and the {@code catch (NumberFormatException)} around it was dead code.
     */
    @Test
    public void shouldParseTheUsualBooleanSpellings() {

        String param = "nuxeo.hyland.cic.test.boolean";
        try {
            for (String truthy : new String[] { "true", "TRUE", "True", "yes", "YES", "on", "1", " true " }) {
                Framework.getProperties().setProperty(param, truthy);
                assertTrue("'" + truthy + "' should be true", ServicesUtils.configParamToBoolean(param, false));
            }

            for (String falsy : new String[] { "false", "FALSE", "no", "NO", "off", "0" }) {
                Framework.getProperties().setProperty(param, falsy);
                assertFalse("'" + falsy + "' should be false", ServicesUtils.configParamToBoolean(param, true));
            }

            // Unparseable: the default wins, and an error is logged.
            Framework.getProperties().setProperty(param, "maybe");
            assertTrue(ServicesUtils.configParamToBoolean(param, true));
            assertFalse(ServicesUtils.configParamToBoolean(param, false));
        } finally {
            Framework.getProperties().remove(param);
        }
    }

    @Test
    public void shouldFallBackToDefaultWhenBooleanParamIsNotSet() {

        String param = "nuxeo.hyland.cic.test.unset.boolean";
        assertTrue(ServicesUtils.configParamToBoolean(param, true));
        assertFalse(ServicesUtils.configParamToBoolean(param, false));
    }

    /**
     * {@code jsonObjectStrToMap} used {@code getString}, which throws on any non-string value although the intent
     * is unambiguous. Typical case: extra HTTP headers passed as an operation parameter.
     */
    @Test
    public void shouldConvertNonStringJsonValues() {

        Map<String, String> map = ServicesUtils.jsonObjectStrToMap("{\"X-Retry\":3,\"X-Flag\":true,\"X-Name\":\"a\"}");

        assertEquals("3", map.get("X-Retry"));
        assertEquals("true", map.get("X-Flag"));
        assertEquals("a", map.get("X-Name"));
    }

    @Test
    public void shouldReturnNullForBlankJsonObjectString() {

        assertNull(ServicesUtils.jsonObjectStrToMap(null));
        assertNull(ServicesUtils.jsonObjectStrToMap(""));
    }

}
