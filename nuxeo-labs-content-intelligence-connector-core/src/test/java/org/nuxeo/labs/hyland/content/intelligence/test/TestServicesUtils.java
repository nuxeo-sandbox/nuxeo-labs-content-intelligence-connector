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

import org.junit.Test;
import org.nuxeo.labs.hyland.content.intelligence.service.ServicesUtils;

/**
 * Pure unit tests for {@link ServicesUtils}. No Nuxeo runtime, no CIC credentials: these run everywhere.
 *
 * @since 2025.22
 */
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

}
