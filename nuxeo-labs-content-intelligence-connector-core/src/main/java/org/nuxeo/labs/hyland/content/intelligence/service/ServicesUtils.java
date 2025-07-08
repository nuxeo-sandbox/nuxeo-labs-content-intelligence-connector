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
import org.json.JSONObject;

/**
 * Shared utilities. Just DRY pattern.
 * 
 * @since 2023
 */
public class ServicesUtils {

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

        // Unfortunately, jsonObject.toMap() returns a Map<String, Object> that would require extra conversion.
        // We need to loop.
        Map<String, String> map = new HashMap<String, String>();
        Iterator<String> keys = jsonObject.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            map.put(key, jsonObject.getString(key));
        }

        return map;

    }
}
