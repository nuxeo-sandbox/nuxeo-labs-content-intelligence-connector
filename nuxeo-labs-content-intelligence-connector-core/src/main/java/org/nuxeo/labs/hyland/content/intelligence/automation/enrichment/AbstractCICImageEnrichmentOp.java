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

import org.json.JSONObject;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.HylandKEService;
import org.nuxeo.runtime.api.Framework;

/**
 * Helper for the CIC image-* operations: extracts a JPEG rendition via the
 * {@code MultiviewPicture} adapter.
 *
 * @since 2025.18
 */
public abstract class AbstractCICImageEnrichmentOp extends AbstractCICEnrichmentOp {

    protected String renditionName;

    protected String configName;

    @Override
    protected Blob getBlob(DocumentModel doc) {
        HylandKEService ke = Framework.getService(HylandKEService.class);
        // Errors are recorded by the service when the picture facet/view is missing.
        return ke.getImageRenditionForCIC(doc, configName, renditionName, true);
    }

    /**
     * Restores the image-op-specific fields ({@code configName}, {@code renditionName}) when the
     * op runs inside {@link CICEnrichmentWork}. Concrete subclasses with additional params MUST
     * override and call {@code super.applyAsyncParams(params)} first.
     *
     * @since 2025.16
     */
    @Override
    public void applyAsyncParams(JSONObject params) {
        super.applyAsyncParams(params);
        this.configName = params.has("configName") ? params.optString("configName", null) : null;
        this.renditionName = params.has("renditionName") ? params.optString("renditionName", null) : null;
    }

}
