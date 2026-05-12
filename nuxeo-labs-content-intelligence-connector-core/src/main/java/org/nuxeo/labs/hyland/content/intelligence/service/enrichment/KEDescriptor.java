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
 *     Thibaud Arguillere
 */
package org.nuxeo.labs.hyland.content.intelligence.service.enrichment;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.labs.hyland.content.intelligence.service.AbstractServiceDescriptor;

/**
 * @since 2023
 */
@XObject("knowledgeEnrichment")
public class KEDescriptor extends AbstractServiceDescriptor {

    private static final Logger LOG = LogManager.getLogger(KEDescriptor.class);

    /**
     * Optional. Facet to add on the document when persisting embeddings produced by enrichment ops
     * such as {@code CIC.GetTextEmbeddings} / {@code CIC.GetImageDescriptionAndEmbeddings} /
     * {@code CIC.ImageGetAll}.
     * <p>
     * The facet itself (and its schema providing the multi-valued double[] fields) must be declared
     * elsewhere (typically by another plugin or by the studio project that owns the embeddings storage).
     * This plugin does NOT ship an embeddings facet/schema, so cross-plugin contracts are preserved.
     * <p>
     * If left blank, the embedding-writing helpers become a no-op (the API call still runs and the
     * JSON is returned to the caller, but nothing is written on the document).
     *
     * @since 2025.18
     */
    @XNode("embeddingsFacet")
    protected String embeddingsFacet;

    /**
     * Optional. XPath of the multi-valued double[] field where image embeddings are stored.
     * Example: {@code embeddings:image}.
     *
     * @since 2025.18
     */
    @XNode("embeddingsImageXpath")
    protected String embeddingsImageXpath;

    /**
     * Optional. XPath of the multi-valued double[] field where text embeddings are stored.
     * Example: {@code embeddings:text}.
     *
     * @since 2025.18
     */
    @XNode("embeddingsTextXpath")
    protected String embeddingsTextXpath;

    /**
     * Optional. Picture rendition (view) name used by the CIC.* operations to retrieve a JPEG
     * rendition of an image document via the {@code MultiviewPicture} adapter when no explicit
     * rendition is provided by the caller.
     * <p>
     * Defaults to {@value #DEFAULT_PICTURE_RENDITION_NAME} when not configured.
     *
     * @since 2025.18
     */
    @XNode("pictureRenditionName")
    protected String pictureRenditionName;

    /** @since 2025.18 */
    public static final String DEFAULT_PICTURE_RENDITION_NAME = "FullHD";

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected String serviceLabel() {
        return HylandKEService.SERVICE_LABEL;
    }

    @Override
    protected boolean requiresEnvironment() {
        return false;
    }

    @Override
    public String getEnvironment() {
        return null;
    }

    /**
     * @since 2025.18
     */
    public String getEmbeddingsFacet() {
        return embeddingsFacet;
    }

    /**
     * @since 2025.18
     */
    public String getEmbeddingsImageXpath() {
        return embeddingsImageXpath;
    }

    /**
     * @since 2025.18
     */
    public String getEmbeddingsTextXpath() {
        return embeddingsTextXpath;
    }

    /**
     * Returns the configured picture rendition name, or {@link #DEFAULT_PICTURE_RENDITION_NAME}
     * if none was configured.
     *
     * @since 2025.18
     */
    public String getPictureRenditionName() {
        return (pictureRenditionName == null || pictureRenditionName.isBlank())
                ? DEFAULT_PICTURE_RENDITION_NAME
                : pictureRenditionName;
    }

}
