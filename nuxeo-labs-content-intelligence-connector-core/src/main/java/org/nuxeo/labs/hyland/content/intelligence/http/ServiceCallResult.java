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
package org.nuxeo.labs.hyland.content.intelligence.http;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.NuxeoException;

/**
 * Class handling the result of a HTTP call to the service.
 *
 * @since 2023
 */
public class ServiceCallResult {

    protected String response;

    protected int responseCode;

    protected String responseMessage;

    protected JSONArray objectKeysMapping = null;

    public ServiceCallResult(String response, int responseCode, String responseMessage) {
        super();
        this.response = response;
        this.responseCode = responseCode;
        this.responseMessage = responseMessage;
    }

    public ServiceCallResult(String response, int responseCode, String responseMessage, JSONArray objectKeysMapping) {
        super();
        this.response = response;
        this.responseCode = responseCode;
        this.responseMessage = responseMessage;
        this.objectKeysMapping = objectKeysMapping;
    }

    /**
     * Rebuilds a result from the JSON envelope produced by {@link #toJsonString()}. Mainly used in unit tests.
     * <p>
     * Every field is read with an {@code opt*} accessor: {@code response} may legitimately be an array rather
     * than an object, and {@code objectKeysMapping} is absent from most envelopes because {@code JSONObject.put}
     * removes a key whose value is {@code null}.
     *
     * @since 2023
     */
    public ServiceCallResult(String jsonStr) {
        JSONObject obj = new JSONObject(jsonStr);

        Object responseValue = obj.opt("response");
        response = (responseValue == null || responseValue == JSONObject.NULL) ? null : responseValue.toString();
        responseCode = obj.optInt("responseCode", -1);
        responseMessage = obj.optString("responseMessage", "");
        objectKeysMapping = obj.optJSONArray("objectKeysMapping");
    }

    /**
     * @return the JSON object of thsi object
     * @since 2023
     */
    public JSONObject toJsonObject() {

        JSONObject obj = new JSONObject();

        if (StringUtils.isNotBlank(response)) {
            obj.put("response", parseResponseOrRaw(response));
        } else {
            if (isHttpSuccess(responseCode)) {
                obj.put("response", new JSONObject("{\"errorMessage\": \"Empty string as response\"}"));
            } else {
                obj.put("response", new JSONObject("{}"));
            }
        }
        obj.put("responseCode", responseCode);
        obj.put("responseMessage", (responseMessage == null ? "" : responseMessage));
        obj.put("objectKeysMapping", objectKeysMapping);

        return obj;
    }

    /**
     * Parses {@code raw} as a JSON object or array, falling back to the raw string when it is neither.
     * <p>
     * The fallback matters since the HTTP layer started forwarding the body of failed calls: Content Intelligence
     * answers with JSON, but an intermediate gateway or proxy may well return an HTML error page. Putting the
     * string as-is keeps the envelope valid JSON and preserves the payload, instead of throwing a JSONException
     * while building an error report.
     *
     * @since 2025.22
     */
    protected static Object parseResponseOrRaw(String raw) {

        String trimmed = raw.trim();
        try {
            if (trimmed.startsWith("{")) {
                return new JSONObject(trimmed);
            }
            if (trimmed.startsWith("[")) {
                return new JSONArray(trimmed);
            }
        } catch (JSONException e) {
            // Not valid JSON after all: fall through and keep the raw string.
        }
        return raw;
    }

    /**
     * @return the JSON String of this object
     * @since 2023
     */
    public String toJsonString() {

        return toJsonString(0);
    }

    public String toJsonString(int indentFactor) {

        JSONObject obj = toJsonObject();
        return obj.toString(indentFactor);
    }

    /**
     * Some APIs don't return a JSON object (nor array).
     * And it even may be quoted/double quoted in the response.
     *
     * @return the response. If it both starts and ends with a double quote, these are removed.
     * @since 2023
     */
    public String getResponse() {

        if (StringUtils.isBlank(response)) {
            return response;
        }

        // Both ends must be checked: a truncated response starting with a quote would otherwise lose its last
        // character.
        if (response.length() > 1 && response.startsWith("\"") && response.endsWith("\"")) {
            return response.substring(1, response.length() - 1);
        }

        return response;
    }

    /**
     * Set the response. SHould eb JSON string.
     *
     * @since 2025.16 (note: not properly tracked, exact first-release version unknown)
     */
    public void setResponse(String response) {
        this.response = response;
    }

    /**
     * Return the response from the service as JSONObject. Throws an exception if the response cannot be parsed as JSON
     *
     * @return the response from the service as JSONObject
     * @since 2023
     */
    public JSONObject getResponseAsJSONObject() {
        if (response != null && !response.startsWith("{") && !response.startsWith("[")) {
            throw new NuxeoException(
                    "response is a simple string, cannot be converted to JSON Object. Call getResponse() instead.");
        }
        return new JSONObject(response);
    }

    /**
     * Always return a JSON Object with a single field, "result", holding the raw response (that can be a simple
     * String, or JSON).
     * <p>
     * Building the JSON through {@link JSONObject#put} rather than by string concatenation is deliberate: it
     * delegates escaping to the library. The previous implementation concatenated the raw value between quotes,
     * which produced invalid JSON as soon as the response contained a quote, and it assigned instead of appending
     * in its last branch, so a plain-string response yielded {@code "xxx"}} and made {@code new JSONObject(...)}
     * throw. It also called {@code new JSONObject(...)} on a response starting with {@code [}, which always
     * throws.
     *
     * @since 2025.16 (note: not properly tracked, exact first-release version unknown)
     */
    public JSONObject forceResponseAsJSONObject() {

        JSONObject result = new JSONObject();

        if (response == null) {
            return result.put("result", JSONObject.NULL);
        }

        return result.put("result", parseResponseOrRaw(getResponse()));
    }

    public void setObjectKeysMapping(JSONArray mapping) {
        this.objectKeysMapping = mapping;
    }

    public JSONArray getObjectKeysMapping() {
        return objectKeysMapping;
    }

    /**
     * Return the response from the service as JSONArray. Throws an exception if the response cannot be parsed as JSON
     *
     * @return the response from the service as JSONArray
     * @since 2023
     */
    public JSONArray getResponseAsJSONArray() {
        if (response != null && !response.startsWith("[")) {
            throw new NuxeoException(
                    "response is a simple string, cannot be converted to JSON Array. Call getResponse() instead.");
        }
        return new JSONArray(response);
    }

    public int getResponseCode() {
        return responseCode;
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    /**
     * @return true if responseCode is in the "OK" range
     * @since 2023
     */
    public boolean callWasSuccesful() {
        return isHttpSuccess(responseCode);
    }

    /**
     * @return true if responseCode is 200
     * @since 2023
     */
    public boolean callResponseOK() {
        return responseCode == 200;
    }

    /**
     * @return true if responseCode is not in the "OK" range
     * @since 2023
     */
    public boolean callFailed() {
        return !isHttpSuccess(responseCode);
    }

    /**
     * @return true if statusCode is 200
     * @since 2023
     */
    public static boolean isHttpOk(int statusCode) {
        return statusCode == 200;
    }

    /**
     * @return true if statusCode is in the "OK" range
     * @since 2023
     */
    public static boolean isHttpSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

}
