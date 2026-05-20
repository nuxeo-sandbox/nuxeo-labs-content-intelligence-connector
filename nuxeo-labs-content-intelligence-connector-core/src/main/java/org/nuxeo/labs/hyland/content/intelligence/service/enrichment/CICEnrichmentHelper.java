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
package org.nuxeo.labs.hyland.content.intelligence.service.enrichment;

import org.json.JSONObject;
import org.nuxeo.ecm.core.api.DocumentModel;

/**
 * Helper service for the CIC.* document operations.
 * <p>
 * Concentrates two responsibilities:
 * <ul>
 * <li>Parsing the canonical CIC envelope ({@code {responseCode, responseMessage, response, ...}})
 * returned by {@link HylandKEService}.</li>
 * <li>Writing the parsed enrichment results into the {@code cic_*} schemas / facets that ship with
 * this plugin (summary, classification, named entities, metadata detection, image description, text
 * metadata, embeddings).</li>
 * </ul>
 * The helper does NOT save the document; callers are responsible for {@code doc = session.saveDocument(doc)}
 * (always reassign so the in-memory model reflects the saved state for any subsequent reads).
 * <p>
 * Embeddings writers are no-ops unless the enrichment descriptor is configured with
 * {@code embeddingsFacet} + {@code embeddingsImageXpath} / {@code embeddingsTextXpath} (see
 * {@link KEDescriptor}).
 *
 * @since 2025.18
 */
public interface CICEnrichmentHelper {

    /**
     * Parses the JSON string returned by an enrichment call (typically the canonical envelope
     * {@code {responseCode, responseMessage, response, ...}}). Returns {@code null} when the input
     * is blank or unparseable.
     */
    JSONObject parseEnrichmentResponse(String responseJsonString);

    /**
     * Returns the result payload for the given action key (e.g. {@code "textSummarization"}) inside
     * the canonical CIC envelope, or {@code null} if not present.
     * <p>
     * The CIC contract is roughly: {@code envelope.response.results[0].<actionKey>.result}.
     */
    Object extractActionResult(JSONObject envelope, String actionKey);

    /** Adds the {@code CICSummary} facet (if needed) and writes {@code cic_summary:summary}. */
    void writeSummary(DocumentModel doc, String summary);

    /**
     * Adds the {@code CICImageDescription} facet (if needed) and writes
     * {@code cic_image_description:description}.
     */
    void writeImageDescription(DocumentModel doc, String description);

    /**
     * Adds the {@code CICClassification} facet (if needed) and writes the given classification
     * field, which must be either {@code "imageClass"} or {@code "textClass"}.
     */
    void writeClassification(DocumentModel doc, String fieldName, String value);

    /**
     * Adds the {@code CICNamedEntities} facet (if needed) and writes the
     * {@code cic_named_entities:entities} list from the parsed action result.
     * <p>
     * The expected JSON shape is the CIC named-entity result (an object whose top-level keys are
     * entity names mapped to a list of values).
     */
    void writeNamedEntities(DocumentModel doc, Object namedEntitiesResult);

    /**
     * Adds the {@code CICMetadataDetection} facet (if needed) and writes the
     * {@code cic_metadata_detection:metadata} list from the parsed action result.
     * <p>
     * The expected JSON shape is the CIC metadata-detection result (object of {@code field -> value}).
     * Each value is converted with {@link #metadataJsonValueToString(Object)}.
     */
    void writeMetadataDetection(DocumentModel doc, Object metadataResult);

    /**
     * Writes text embeddings on the document if the descriptor is configured. No-op otherwise.
     *
     * @param doc        the target document
     * @param configName KE contribution name (used to resolve embeddings facet/xpath); may be {@code null}
     * @param embeddings the array of double values returned by the API
     */
    void writeTextEmbeddings(DocumentModel doc, String configName, Object embeddings);

    /**
     * Writes image embeddings on the document if the descriptor is configured. No-op otherwise.
     */
    void writeImageEmbeddings(DocumentModel doc, String configName, Object embeddings);

    /**
     * Faithful Java port of the Studio JS {@code metadataJsonValueToString(value)} algorithm used
     * by the metadata-detection writers.
     * <p>
     * Behaviour summary:
     * <ul>
     * <li>{@code null} input => {@code null}.</li>
     * <li>Scalar => {@code String.valueOf(value)}.</li>
     * <li>Empty array => {@code ""}.</li>
     * <li>Array of scalars => comma-joined ({@code [].join()} JS semantics: items separated by
     * {@code ","}).</li>
     * <li>Array of arrays => one line per sub-array, items inside a line joined with {@code ","}.</li>
     * <li>Array of objects => one line per object: {@code "key: val, key: val"}.</li>
     * <li>Mixed array => one JSON-stringified entry per line.</li>
     * <li>Object => {@code "key\n  value"} blocks separated by a blank line.</li>
     * </ul>
     * Additionally, if the resulting string is an ISO date (YYYY-MM-DD), the {@code -} are replaced
     * by the {@code U+2022} bullet character (preserves the Studio behaviour that avoided
     * Elasticsearch type clashes).
     */
    String metadataJsonValueToString(Object value);

}
