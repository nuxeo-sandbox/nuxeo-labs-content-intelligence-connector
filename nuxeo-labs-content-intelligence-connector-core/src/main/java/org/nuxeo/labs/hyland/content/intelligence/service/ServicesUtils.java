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
package org.nuxeo.labs.hyland.content.intelligence.service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.HylandKEService;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.HylandKEServiceImpl;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.osgi.OSGiRuntimeService;

/**
 * Shared utilities. Just DRY pattern.
 *
 * @since 2023
 */
public class ServicesUtils {

    private static final Logger log = LogManager.getLogger(ServicesUtils.class);

    /**
     * If jsonObjectStr is null or empty, returns null
     *
     * @since 2023
     */
    public static Map<String, String> jsonObjectStrToMap(String jsonObjectStr) {

        if (StringUtils.isBlank(jsonObjectStr)) {
            return null;
        }

        JSONObject jsonObject = new JSONObject(jsonObjectStr);

        Map<String, String> map = new HashMap<>();
        Iterator<String> keys = jsonObject.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            map.put(key, jsonObject.getString(key));
        }

        return map;

    }

    /**
     * Converter handling errors.
     *
     * @since 2025.16 (note: not properly tracked, exact first-release version unknown)
     */
    public static int configParamToInt(String param, int defaultValue) {

        int value;

        String paramValue = Framework.getProperty(param, "" + defaultValue);
        try {
            value = Integer.parseInt(paramValue);
        } catch (NumberFormatException e) {
            log.error("Parameter <{}> is not a valid integer. Using default value", param);
            value = defaultValue;
        }

        return value;
    }

    /**
     * Converter handling errors.
     *
     * @since 2025.16 (note: not properly tracked, exact first-release version unknown)
     */
    public static boolean configParamToBoolean(String param, boolean defaultValue) {

        boolean value;

        String paramValue = Framework.getProperty(param, "" + defaultValue);
        try {
            value = Boolean.parseBoolean(paramValue);
        } catch (NumberFormatException e) {
            log.error("Parameter <{}> is not a valid boolean. Using default value", param);
            value = defaultValue;
        }

        return value;
    }

    /**
     * Centralize KE operations parameters handling since the new "instructions" property available in KE V2.
     * basically, add them to the extraJsonPayload. See "About KE v1->v2 compatibility and format" in
     * {@link HylandKEServiceImpl}.
     * If instructionsV2JsonStr is empty => returns extraPayloadJsonStr unchanged (may be null)
     *
     * @since 2025.16 (note: not properly tracked, exact first-release version unknown)
     */
    public static String addInstructionsToExtraPayload(String instructionsV2JsonStr, String extraPayloadJsonStr) {

        if (StringUtils.isBlank(instructionsV2JsonStr)) {
            return extraPayloadJsonStr;
        }

        HylandKEService keService = Framework.getService(HylandKEService.class);

        if (!keService.getUseKEV2()) {
            return extraPayloadJsonStr;
        }

        JSONObject extraPayload = null;
        if (StringUtils.isBlank(extraPayloadJsonStr)) {
            extraPayload = new JSONObject();
        } else {
            extraPayload = new JSONObject(extraPayloadJsonStr);
        }

        extraPayload.put(HylandKEServiceImpl.KE_INSTRUCTIONS_OBJ_IN_EXTRA_PAYLOAD,
                new JSONObject(instructionsV2JsonStr));

        return extraPayload.toString();

    }

    /**
     * We don't want to log WARN for info, and we don't want to contribute a logger.
     * So we use one that is able to display INFO.
     * @since 2025.16 (note: not properly tracked, exact first-release version unknown)
     */
    public static void forceLogInfo(Class<?> clazz, String message) {

        Logger tempLogger = LogManager.getLogger(OSGiRuntimeService.class);
        String msg = "[On behalf " + clazz.getName() + "] " + message;
        tempLogger.info(msg);
    }

    /**
     * Tells whether the global {@code nuxeo.hyland.cic.moreLogs} configuration parameter is enabled.
     * <p>
     * The value is read on every call: {@link Framework#getProperty} is a cheap in-memory lookup, negligible
     * compared to the HTTP call to Content Intelligence it precedes. Caching it in a static field would also make
     * both values impossible to exercise in the same JVM at test time.
     *
     * @return true when extra INFO logging is requested
     * @since 2025.20
     */
    public static boolean isMoreLogs() {

        return configParamToBoolean(CICServiceConstants.MORE_LOGS_PARAM, CICServiceConstants.MORE_LOGS_DEFAULT);
    }

    /**
     * Logs, at INFO level, a single call to Content Intelligence, but only when {@link #isMoreLogs()} is true.
     * Does nothing otherwise, so callers never need to guard the call.
     * <p>
     * This helper is service-agnostic on purpose: it is meant to be used by every service family, not only by
     * Knowledge Enrichment. The resulting message is
     * {@code "Calling CIC <service>/<action> for <target>"}, for example
     * {@code "Calling CIC KE/imageDescription for document 1234-5678"}.
     *
     * @param clazz the calling class, reported by {@link #forceLogInfo}
     * @param service a short service code, see the {@code SERVICE_CODE_*} constants in {@link CICServiceConstants}
     * @param action the action/endpoint being called. When blank, it is simply omitted from the message
     * @param target a human readable description of what is being processed, typically built with
     *            {@link #targetDocument}, {@link #targetDocuments} or {@link #targetBlobs}. When blank, the
     *            {@code " for ..."} part is omitted
     * @since 2025.20
     */
    public static void logCICCall(Class<?> clazz, String service, String action, String target) {

        if (!isMoreLogs()) {
            return;
        }

        StringBuilder msg = new StringBuilder("Calling CIC ").append(service);
        if (StringUtils.isNotBlank(action)) {
            msg.append("/").append(action);
        }
        if (StringUtils.isNotBlank(target)) {
            msg.append(" for ").append(target);
        }

        forceLogInfo(clazz, msg.toString());
    }

    /**
     * Same as {@link #logCICCall(Class, String, String, String)} without a target description.
     *
     * @since 2025.20
     */
    public static void logCICCall(Class<?> clazz, String service, String action) {

        logCICCall(clazz, service, action, null);
    }

    /**
     * Formats a single document as a {@link #logCICCall} target: {@code "document <id>"}.
     *
     * @since 2025.20
     */
    public static String targetDocument(String docId) {

        return "document " + docId;
    }

    /**
     * Formats a document count as a {@link #logCICCall} target: {@code "<n> documents"}.
     *
     * @since 2025.20
     */
    public static String targetDocuments(int count) {

        return count + (count == 1 ? " document" : " documents");
    }

    /**
     * Formats a blob count as a {@link #logCICCall} target: {@code "<n> blobs"}.
     *
     * @since 2025.20
     */
    public static String targetBlobs(int count) {

        return count + (count == 1 ? " blob" : " blobs");
    }
}
