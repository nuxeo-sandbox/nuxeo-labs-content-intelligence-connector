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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility class, centralizing the HTTP calls and returning a <code>ServiceCallResult</code>
 *
 * @since 2023
 */
public class ServiceCall {

    private static final Logger log = LogManager.getLogger(ServiceCall.class);

    /**
     * Perform a GET call.
     * <p>
     * Query params, if any, must be handled by the caller (and appended to the url, with the correct encoding)
     *
     * @param url the full URL to call, query params included
     * @param headers the request headers. Can be null.
     * @return a ServiceCallResult, never null. On failure, its response code is -1.
     * @since 2023
     */
    public ServiceCallResult get(String url, Map<String, String> headers) {

        ServiceCallResult result;

        HttpURLConnection connection = null;
        try {
            // Create the URL object
            var theUrl = new URI(url).toURL();
            connection = (HttpURLConnection) theUrl.openConnection();
            connection.setRequestMethod("GET");

            if (headers != null) {
                headers.forEach(connection::setRequestProperty);
            }

            result = readResponse(connection);

        } catch (IOException | URISyntaxException e) {
            log.error("Error calling GET {}", url, e);
            result = new ServiceCallResult("{}", -1, e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return result;
    }

    /*
     * Just to centralize the calls. For now, they are the same
     * (may change in the future, depending on the change sin the service API)
     */
    protected ServiceCallResult postOrPut(String httpMethod, String url, Map<String, String> headers, String body) {

        ServiceCallResult result;

        HttpURLConnection connection = null;
        try {
            // Create the URL object
            var theUrl = new URI(url).toURL();
            connection = (HttpURLConnection) theUrl.openConnection();
            // POST or PUT
            connection.setRequestMethod(httpMethod);

            if (headers != null) {
                headers.forEach(connection::setRequestProperty);
            }

            connection.setDoOutput(true);
            if (body != null) {
                try (var os = connection.getOutputStream()) {
                    var input = body.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
            }

            result = readResponse(connection);

        } catch (IOException | URISyntaxException e) {
            log.error("Error calling {} {}", httpMethod, url, e);
            result = new ServiceCallResult("{}", -1, e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return result;
    }

    public ServiceCallResult post(String url, Map<String, String> headers, String body) {

        return postOrPut("POST", url, headers, body);
    }

    public ServiceCallResult put(String url, Map<String, String> headers, String body) {

        return postOrPut("PUT", url, headers, body);
    }

    /**
     * Upload a file with a PUT call.
     * <p>
     * The "response" field of the returned <code>ServiceCallResult</code> is always an empty JSON object, "{}".
     *
     * @param file the file to upload. Must exist and be a regular file.
     * @param targetUrl the URL to PUT the file to
     * @param contentType the value of the Content-Type request header
     * @return a ServiceCallResult, never null. On failure, its response code is -1.
     * @since 2023
     */
    public ServiceCallResult uploadFileWithPut(File file, String targetUrl, String contentType) {

        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("Invalid file: " + file.getAbsolutePath());
        }

        ServiceCallResult result;

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URI(targetUrl).toURL().openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("PUT");
            connection.setRequestProperty("Content-Type", contentType);
            connection.setFixedLengthStreamingMode(file.length());

            try (var out = connection.getOutputStream(); var in = Files.newInputStream(file.toPath())) {

                var buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();

                result = new ServiceCallResult("{}", connection.getResponseCode(), connection.getResponseMessage());
            }

        } catch (IOException | URISyntaxException e) {
            log.error("Error uploading file with PUT to {}", targetUrl, e);
            result = new ServiceCallResult("{}", -1, e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return result;
    }

    /**
     * Utility, used by other methods (get, post, put), once the call returns a status >= 200 < 300.
     * <p>
     * When the call was not successful, the "response" field of the returned <code>ServiceCallResult</code> is an empty
     * JSON object, "{}".
     *
     * @param connection the connection to read the response from
     * @return a ServiceCallResult holding the response body, code and message
     * @throws IOException if reading the response fails
     * @since 2023
     */
    public ServiceCallResult readResponse(HttpURLConnection connection) throws IOException {

        ServiceCallResult result;

        int responseCode = connection.getResponseCode();
        if (ServiceCallResult.isHttpSuccess(responseCode)) {
            try (var br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                var responseStr = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    responseStr.append(line.trim());
                }
                result = new ServiceCallResult(responseStr.toString(), responseCode, connection.getResponseMessage());
            }
        } else {
            result = new ServiceCallResult("{}", responseCode, connection.getResponseMessage());
        }

        return result;
    }

}
