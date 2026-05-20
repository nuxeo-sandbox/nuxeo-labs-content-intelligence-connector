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
package org.nuxeo.labs.hyland.content.intelligence.automation.enrichment;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;

/**
 * Helper for the CIC text-* operations: extracts the configured blob (default {@code file:content}).
 *
 * @since 2025.18
 */
public abstract class AbstractCICTextEnrichmentOp extends AbstractCICEnrichmentOp {

    public static final String DEFAULT_TEXT_XPATH = "file:content";

    protected String xpath;

    @Override
    protected Blob getBlob(DocumentModel doc) {
        String path = StringUtils.isBlank(xpath) ? DEFAULT_TEXT_XPATH : xpath;
        Object value = doc.getPropertyValue(path);
        return value instanceof Blob b ? b : null;
    }

    /**
     * Restores the text-op-specific field ({@code xpath}) when the op runs inside
     * {@link CICEnrichmentWork}. Concrete subclasses with additional params MUST override and
     * call {@code super.applyAsyncParams(params)} first.
     *
     * @since 2025.16
     */
    @Override
    public void applyAsyncParams(JSONObject params) {
        super.applyAsyncParams(params);
        this.xpath = params.has("xpath") ? params.optString("xpath", null) : null;
    }

}
