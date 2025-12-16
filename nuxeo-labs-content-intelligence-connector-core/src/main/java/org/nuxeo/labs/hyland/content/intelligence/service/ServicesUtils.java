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
package org.nuxeo.labs.hyland.content.intelligence.service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.nuxeo.runtime.api.Framework;

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

        Map<String, String> map = new HashMap<String, String>();
        Iterator<String> keys = jsonObject.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            map.put(key, jsonObject.getString(key));
        }

        return map;

    }

    public static int configParamToInt(String param, int defaultValue) {

        int value;

        String paramValue = Framework.getProperty(param, "" + defaultValue);
        try {
            value = Integer.parseInt(paramValue);
        } catch (NumberFormatException e) {
            log.error("Parameter <" + param + "> is not a valid integer. Using default value");
            value = defaultValue;
        }

        return value;
    }
}
