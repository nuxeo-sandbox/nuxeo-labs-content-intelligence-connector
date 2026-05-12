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
 *     Thibaud Arguillere
 */
package org.nuxeo.labs.hyland.content.intelligence.service.enrichment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * Default implementation of {@link CICEnrichmentHelper}.
 *
 * @since 2025.18
 */
public class CICEnrichmentHelperImpl extends DefaultComponent implements CICEnrichmentHelper {

    private static final Logger LOG = LogManager.getLogger(CICEnrichmentHelperImpl.class);

    /* ==================== Facet / schema constants ==================== */

    public static final String FACET_SUMMARY = "CICSummary";

    public static final String FACET_IMAGE_DESCRIPTION = "CICImageDescription";

    public static final String FACET_CLASSIFICATION = "CICClassification";

    public static final String FACET_NAMED_ENTITIES = "CICNamedEntities";

    public static final String FACET_METADATA_DETECTION = "CICMetadataDetection";

    public static final String FIELD_SUMMARY = "cic_summary:summary";

    public static final String FIELD_IMAGE_DESCRIPTION = "cic_image_description:description";

    public static final String FIELD_CLASSIFICATION_PREFIX = "cic_classification:";

    public static final String FIELD_NAMED_ENTITIES = "cic_named_entities:entities";

    public static final String FIELD_METADATA_DETECTION = "cic_metadata_detection:metadata";

    public static final String FIELD_TEXT_METADATA_COMPANY = "cic_text_metadata:company";

    public static final String FIELD_TEXT_METADATA_KEYWORDS = "cic_text_metadata:keywords";

    public static final String FIELD_TEXT_METADATA_MORE = "cic_text_metadata:moreMetadata";

    public static final String FIELD_TEXT_METADATA_OWNER = "cic_text_metadata:owner";

    public static final String FIELD_TEXT_METADATA_SECURITY = "cic_text_metadata:security";

    private static final Pattern ISO_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private static final String BULLET = "\u2022";

    /* ==================== Parser ==================== */

    @Override
    public JSONObject parseEnrichmentResponse(String responseJsonString) {
        if (StringUtils.isBlank(responseJsonString)) {
            return null;
        }
        try {
            return new JSONObject(responseJsonString);
        } catch (JSONException e) {
            LOG.warn("Failed to parse enrichment response as JSON: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public Object extractActionResult(JSONObject envelope, String actionKey) {
        if (envelope == null || StringUtils.isBlank(actionKey)) {
            return null;
        }
        // envelope.response.results[0].<actionKey>.result
        JSONObject response = envelope.optJSONObject("response");
        if (response == null) {
            return null;
        }
        JSONArray results = response.optJSONArray("results");
        if (results == null || results.isEmpty()) {
            return null;
        }
        JSONObject first = results.optJSONObject(0);
        if (first == null) {
            return null;
        }
        JSONObject action = first.optJSONObject(actionKey);
        if (action == null) {
            return null;
        }
        return action.opt("result");
    }

    /* ==================== Facet writers ==================== */

    @Override
    public void writeSummary(DocumentModel doc, String summary) {
        ensureFacet(doc, FACET_SUMMARY);
        doc.setPropertyValue(FIELD_SUMMARY, summary);
    }

    @Override
    public void writeImageDescription(DocumentModel doc, String description) {
        ensureFacet(doc, FACET_IMAGE_DESCRIPTION);
        doc.setPropertyValue(FIELD_IMAGE_DESCRIPTION, description);
    }

    @Override
    public void writeClassification(DocumentModel doc, String fieldName, String value) {
        if (StringUtils.isBlank(fieldName)) {
            throw new NuxeoException("fieldName is required (imageClass or textClass)");
        }
        ensureFacet(doc, FACET_CLASSIFICATION);
        doc.setPropertyValue(FIELD_CLASSIFICATION_PREFIX + fieldName, value);
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void writeNamedEntities(DocumentModel doc, Object namedEntitiesResult) {
        ensureFacet(doc, FACET_NAMED_ENTITIES);
        List<Map<String, Object>> items = new ArrayList<>();
        if (namedEntitiesResult instanceof JSONObject jo) {
            for (String key : jo.keySet()) {
                Object raw = jo.opt(key);
                Map<String, Object> item = new HashMap<>();
                item.put("entity", key);
                item.put("values", toStringList(raw));
                items.add(item);
            }
        } else if (namedEntitiesResult instanceof JSONArray ja) {
            // Tolerate alt shape: [{entity:..., values:[...]} ...]
            for (int i = 0; i < ja.length(); i++) {
                JSONObject jo = ja.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                Map<String, Object> item = new HashMap<>();
                item.put("entity", jo.optString("entity", ""));
                item.put("values", toStringList(jo.opt("values")));
                items.add(item);
            }
        }
        doc.setPropertyValue(FIELD_NAMED_ENTITIES, (java.io.Serializable) (List) items);
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void writeMetadataDetection(DocumentModel doc, Object metadataResult) {
        ensureFacet(doc, FACET_METADATA_DETECTION);
        List<Map<String, Object>> items = new ArrayList<>();
        if (metadataResult instanceof JSONObject jo) {
            for (String key : jo.keySet()) {
                Map<String, Object> item = new HashMap<>();
                item.put("field", key);
                item.put("value", metadataJsonValueToString(jo.opt(key)));
                items.add(item);
            }
        } else if (metadataResult instanceof JSONArray ja) {
            // Tolerate alt shape: [{field:..., value:...} ...]
            for (int i = 0; i < ja.length(); i++) {
                JSONObject jo = ja.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                Map<String, Object> item = new HashMap<>();
                item.put("field", jo.optString("field", ""));
                item.put("value", metadataJsonValueToString(jo.opt("value")));
                items.add(item);
            }
        }
        doc.setPropertyValue(FIELD_METADATA_DETECTION, (java.io.Serializable) (List) items);
    }

    @Override
    public void writeTextEmbeddings(DocumentModel doc, String configName, Object embeddings) {
        writeEmbeddings(doc, configName, embeddings, false);
    }

    @Override
    public void writeImageEmbeddings(DocumentModel doc, String configName, Object embeddings) {
        writeEmbeddings(doc, configName, embeddings, true);
    }

    @Override
    public void writeTextMetadata(DocumentModel doc, Object textMetadataResult) {
        if (!(textMetadataResult instanceof JSONObject jo)) {
            return;
        }
        // Studio mapping: company, keywords (array), owner, security, moreMetadata
        if (jo.has("company")) {
            doc.setPropertyValue(FIELD_TEXT_METADATA_COMPANY, jo.optString("company", null));
        }
        if (jo.has("owner")) {
            doc.setPropertyValue(FIELD_TEXT_METADATA_OWNER, jo.optString("owner", null));
        }
        if (jo.has("security")) {
            doc.setPropertyValue(FIELD_TEXT_METADATA_SECURITY, jo.optString("security", null));
        }
        if (jo.has("moreMetadata")) {
            // moreMetadata may be a complex sub-object — flatten with the JS-port formatter.
            doc.setPropertyValue(FIELD_TEXT_METADATA_MORE, metadataJsonValueToString(jo.opt("moreMetadata")));
        }
        if (jo.has("keywords")) {
            doc.setPropertyValue(FIELD_TEXT_METADATA_KEYWORDS,
                    (java.io.Serializable) toStringList(jo.opt("keywords")));
        }
    }

    /* ==================== Helpers ==================== */

    protected void ensureFacet(DocumentModel doc, String facet) {
        if (doc == null) {
            throw new NuxeoException("doc is required");
        }
        if (!doc.hasFacet(facet)) {
            doc.addFacet(facet);
        }
    }

    @SuppressWarnings("unchecked")
    protected List<String> toStringList(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw == JSONObject.NULL) {
            return out;
        }
        if (raw instanceof JSONArray ja) {
            for (int i = 0; i < ja.length(); i++) {
                Object v = ja.opt(i);
                if (v == null || v == JSONObject.NULL) {
                    continue;
                }
                out.add(String.valueOf(v));
            }
            return out;
        }
        if (raw instanceof Collection<?> c) {
            for (Object v : c) {
                if (v == null) {
                    continue;
                }
                out.add(String.valueOf(v));
            }
            return out;
        }
        out.add(String.valueOf(raw));
        return out;
    }

    @SuppressWarnings("unchecked")
    protected void writeEmbeddings(DocumentModel doc, String configName, Object embeddings, boolean image) {
        HylandKEService ke = Framework.getService(HylandKEService.class);
        if (ke == null) {
            return;
        }
        String facet = ke.getEmbeddingsFacet(configName);
        String xpath = image ? ke.getEmbeddingsImageXpath(configName) : ke.getEmbeddingsTextXpath(configName);
        if (StringUtils.isBlank(facet) || StringUtils.isBlank(xpath)) {
            // No-op by design when not configured.
            return;
        }
        if (!doc.hasFacet(facet)) {
            doc.addFacet(facet);
        }
        Double[] values = toDoubleArray(embeddings);
        doc.setPropertyValue(xpath, values);
    }

    protected Double[] toDoubleArray(Object raw) {
        if (raw == null || raw == JSONObject.NULL) {
            return new Double[0];
        }
        if (raw instanceof JSONArray ja) {
            Double[] out = new Double[ja.length()];
            for (int i = 0; i < ja.length(); i++) {
                Object v = ja.opt(i);
                out[i] = (v instanceof Number n) ? n.doubleValue() : 0.0d;
            }
            return out;
        }
        if (raw instanceof Collection<?> c) {
            Double[] out = new Double[c.size()];
            int i = 0;
            for (Object v : c) {
                out[i++] = (v instanceof Number n) ? n.doubleValue() : 0.0d;
            }
            return out;
        }
        if (raw instanceof double[] arr) {
            Double[] out = new Double[arr.length];
            for (int i = 0; i < arr.length; i++) {
                out[i] = arr[i];
            }
            return out;
        }
        if (raw instanceof Number n) {
            return new Double[] { n.doubleValue() };
        }
        return new Double[0];
    }

    /* ==================== JS port: metadataJsonValueToString ==================== */

    /**
     * Type discriminator. Matches the Studio JS {@code theTypeOf(v)} categories:
     * {@code scalar | array-empty | array-scalars | array-arrays | array-objects | array-mixed | object}.
     */
    protected enum ValueType {
        SCALAR, ARRAY_EMPTY, ARRAY_SCALARS, ARRAY_ARRAYS, ARRAY_OBJECTS, ARRAY_MIXED, OBJECT
    }

    protected ValueType theTypeOf(Object v) {
        if (isArrayLike(v)) {
            List<Object> list = toList(v);
            if (list.isEmpty()) {
                return ValueType.ARRAY_EMPTY;
            }
            boolean hasScalar = false;
            boolean hasObject = false;
            boolean hasArray = false;
            for (Object e : list) {
                if (isArrayLike(e)) {
                    hasArray = true;
                } else if (isObjectLike(e)) {
                    hasObject = true;
                } else {
                    hasScalar = true;
                }
            }
            if (hasArray && !hasObject && !hasScalar) {
                return ValueType.ARRAY_ARRAYS;
            }
            if (hasObject && !hasArray && !hasScalar) {
                return ValueType.ARRAY_OBJECTS;
            }
            if (hasScalar && !hasArray && !hasObject) {
                return ValueType.ARRAY_SCALARS;
            }
            return ValueType.ARRAY_MIXED;
        }
        if (isObjectLike(v)) {
            return ValueType.OBJECT;
        }
        return ValueType.SCALAR;
    }

    protected boolean isArrayLike(Object v) {
        return v instanceof JSONArray || v instanceof Collection<?> || (v != null && v.getClass().isArray());
    }

    protected boolean isObjectLike(Object v) {
        if (v == null || v == JSONObject.NULL) {
            return false;
        }
        return v instanceof JSONObject || v instanceof Map<?, ?>;
    }

    @SuppressWarnings("unchecked")
    protected List<Object> toList(Object v) {
        List<Object> out = new ArrayList<>();
        if (v instanceof JSONArray ja) {
            for (int i = 0; i < ja.length(); i++) {
                out.add(ja.opt(i));
            }
            return out;
        }
        if (v instanceof Collection<?> c) {
            out.addAll((Collection<Object>) c);
            return out;
        }
        if (v != null && v.getClass().isArray()) {
            out.addAll(Arrays.asList((Object[]) v));
            return out;
        }
        return out;
    }

    protected Map<String, Object> toMap(Object v) {
        Map<String, Object> out = new HashMap<>();
        if (v instanceof JSONObject jo) {
            for (String key : jo.keySet()) {
                out.put(key, jo.opt(key));
            }
        } else if (v instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return out;
    }

    /** JS {@code JSON.stringify(v)} on objects/arrays; {@code String(v)} on the rest. */
    protected String jsonStringifyOrString(Object v) {
        if (v == null || v == JSONObject.NULL) {
            return "";
        }
        if (v instanceof JSONObject || v instanceof JSONArray) {
            return v.toString();
        }
        if (v instanceof Map<?, ?> || v instanceof Collection<?> || v.getClass().isArray()) {
            try {
                if (v instanceof Map<?, ?> m) {
                    return new JSONObject(m).toString();
                }
                if (v instanceof Collection<?> c) {
                    return new JSONArray(c).toString();
                }
                return new JSONArray(Arrays.asList((Object[]) v)).toString();
            } catch (RuntimeException e) {
                return String.valueOf(v);
            }
        }
        return String.valueOf(v);
    }

    /** Port of JS {@code arrayOfArraysToString}. */
    protected String arrayOfArraysToString(List<Object> arr) {
        List<String> lines = new ArrayList<>();
        for (Object subRaw : arr) {
            if (subRaw == null || subRaw == JSONObject.NULL) {
                lines.add("");
                continue;
            }
            List<Object> sub = toList(subRaw);
            if (sub.isEmpty()) {
                lines.add("");
                continue;
            }
            List<String> parts = new ArrayList<>();
            for (Object item : sub) {
                parts.add(toDisplayInner(item));
            }
            lines.add(String.join(",", parts));
        }
        return String.join("\n", lines);
    }

    /** JS inner toDisplay() in arrayOfArraysToString. */
    protected String toDisplayInner(Object v) {
        if (v == null || v == JSONObject.NULL) {
            return "";
        }
        if (v instanceof String s) {
            return s;
        }
        if (isObjectLike(v) || isArrayLike(v)) {
            return jsonStringifyOrString(v);
        }
        return String.valueOf(v);
    }

    /** Port of JS {@code objectsArrayToString}. */
    protected String objectsArrayToString(List<Object> arr) {
        List<String> lines = new ArrayList<>();
        for (Object objRaw : arr) {
            if (objRaw == null || objRaw == JSONObject.NULL) {
                lines.add("");
                continue;
            }
            Map<String, Object> obj = toMap(objRaw);
            if (obj.isEmpty()) {
                lines.add("");
                continue;
            }
            List<String> parts = new ArrayList<>();
            // Preserve key order when JSONObject
            Iterator<String> it = (objRaw instanceof JSONObject jo) ? jo.keys() : obj.keySet().iterator();
            while (it.hasNext()) {
                String key = it.next();
                Object raw = obj.get(key);
                String valStr = valueToStringObjectsArray(raw);
                if (valStr == null || valStr.isEmpty()) {
                    continue;
                }
                parts.add(key + ": " + valStr);
            }
            lines.add(String.join(", ", parts));
        }
        return String.join("\n", lines);
    }

    /** JS inner valueToString() in objectsArrayToString: falsy => "", object => JSON.stringify. */
    protected String valueToStringObjectsArray(Object v) {
        if (v == null || v == JSONObject.NULL) {
            return "";
        }
        // JS "!v" treats 0, "", false as falsy => "". Match that.
        if (v instanceof String s && s.isEmpty()) {
            return "";
        }
        if (v instanceof Number n && n.doubleValue() == 0.0d) {
            return "";
        }
        if (v instanceof Boolean b && !b) {
            return "";
        }
        if (isObjectLike(v) || isArrayLike(v)) {
            return jsonStringifyOrString(v);
        }
        return String.valueOf(v);
    }

    /** Port of JS {@code mixedArrayToString}. */
    protected String mixedArrayToString(List<Object> arr) {
        if (arr == null || arr.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (Object v : arr) {
            try {
                lines.add(jsonStringifyOrString(v));
            } catch (RuntimeException e) {
                lines.add(String.valueOf(v));
            }
        }
        return String.join("\n", lines);
    }

    /** Port of JS {@code objectToString}. */
    protected String objectToString(Object obj) {
        if (obj == null || obj == JSONObject.NULL) {
            return "";
        }
        Map<String, Object> map = toMap(obj);
        Iterator<String> it = (obj instanceof JSONObject jo) ? jo.keys() : map.keySet().iterator();
        List<String> chunks = new ArrayList<>();
        while (it.hasNext()) {
            String key = it.next();
            Object raw = map.get(key);
            String s = valueToDisplayObject(raw);
            if (s == null) {
                continue;
            }
            // extra blank line before each property name (per JS port)
            chunks.add(key + "\n  " + s);
        }
        return String.join("\n\n", chunks);
    }

    /** JS valueToDisplay() in objectToString: null/undefined/empty-string => null (skip). */
    protected String valueToDisplayObject(Object v) {
        if (v == null || v == JSONObject.NULL) {
            return null;
        }
        if (v instanceof String s && s.isEmpty()) {
            return null;
        }
        if (isArrayLike(v) || isObjectLike(v)) {
            return jsonStringifyOrString(v);
        }
        return String.valueOf(v);
    }

    @Override
    public String metadataJsonValueToString(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        String result;
        switch (theTypeOf(value)) {
            case SCALAR:
                result = String.valueOf(value);
                break;
            case ARRAY_EMPTY:
                result = "";
                break;
            case ARRAY_SCALARS: {
                // JS [].join() defaults to "," separator
                List<Object> list = toList(value);
                List<String> parts = new ArrayList<>(list.size());
                for (Object o : list) {
                    parts.add(o == null || o == JSONObject.NULL ? "" : String.valueOf(o));
                }
                result = String.join(",", parts);
                break;
            }
            case ARRAY_ARRAYS:
                result = arrayOfArraysToString(toList(value));
                break;
            case ARRAY_OBJECTS:
                result = objectsArrayToString(toList(value));
                break;
            case ARRAY_MIXED:
                result = mixedArrayToString(toList(value));
                break;
            case OBJECT:
                result = objectToString(value);
                break;
            default:
                result = jsonStringifyOrString(value);
                break;
        }
        if (ISO_DATE.matcher(result).matches()) {
            result = result.replace("-", BULLET);
        }
        return result;
    }

}
