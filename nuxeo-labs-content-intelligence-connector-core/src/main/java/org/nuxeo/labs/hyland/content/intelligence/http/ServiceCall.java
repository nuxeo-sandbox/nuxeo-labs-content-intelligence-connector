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

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.labs.hyland.content.intelligence.service.CICServiceConstants;
import org.nuxeo.labs.hyland.content.intelligence.service.ServicesUtils;

/**
 * Utility class, centralizing the HTTP calls and returning a <code>ServiceCallResult</code>
 *
 * @since 2023
 */
public class ServiceCall {

    private static final Logger log = LogManager.getLogger(ServiceCall.class);

    /**
     * Opens the connection and applies the configured timeouts.
     * <p>
     * Centralised so no call site can forget them. Without a timeout an unresponsive Content Intelligence
     * endpoint blocks the calling thread forever, which on the {@code cicEnrichment} queue (default
     * {@code maxThreads} of 1) means asynchronous enrichment stops altogether.
     *
     * @since 2025.22
     */
    protected HttpURLConnection openConnection(String url) throws IOException, URISyntaxException {

        var connection = (HttpURLConnection) new URI(url).toURL().openConnection();
        connection.setConnectTimeout(ServicesUtils.configParamToInt(CICServiceConstants.HTTP_CONNECT_TIMEOUT_PARAM,
                CICServiceConstants.HTTP_CONNECT_TIMEOUT_DEFAULT));
        connection.setReadTimeout(ServicesUtils.configParamToInt(CICServiceConstants.HTTP_READ_TIMEOUT_PARAM,
                CICServiceConstants.HTTP_READ_TIMEOUT_DEFAULT));
        return connection;
    }

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
            connection = openConnection(url);
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
            connection = openConnection(url);
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
            connection = openConnection(targetUrl);
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
            }

            int responseCode = connection.getResponseCode();
            String body = ServiceCallResult.isHttpSuccess(responseCode) ? "{}" : readErrorBody(connection);
            result = new ServiceCallResult(body, responseCode, connection.getResponseMessage());

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
     * Reads the body of a failed response.
     * <p>
     * {@code getInputStream()} throws on an error status: the body is only reachable through
     * {@code getErrorStream()}. Discarding it, as the plugin did before 2025.22, meant every diagnostic sent by
     * Content Intelligence was lost and {@code cic_error:fullResponseJson} only ever contained {@code "{}"},
     * while the actual cause sat in the body. Draining the stream also lets the JVM reuse the connection.
     *
     * @param connection the connection whose status is not in the 2xx range
     * @return the error body, or {@code "{}"} when there is none or it cannot be read
     * @since 2025.22
     */
    protected String readErrorBody(HttpURLConnection connection) {

        try (var errorStream = connection.getErrorStream()) {
            if (errorStream == null) {
                return "{}";
            }
            String body = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
            return StringUtils.isBlank(body) ? "{}" : body;
        } catch (IOException e) {
            log.debug("Could not read the error body", e);
            return "{}";
        }
    }

    /**
     * Utility, used by other methods (get, post, put).
     * <p>
     * On a non-2xx status the body is read from the error stream, so the reason for the failure reaches the
     * caller and ends up in {@code cic_error:fullResponseJson}.
     *
     * @param connection the connection to read the response from
     * @return a ServiceCallResult holding the response body, code and message
     * @throws IOException if reading the response fails
     * @since 2023
     */
    public ServiceCallResult readResponse(HttpURLConnection connection) throws IOException {

        int responseCode = connection.getResponseCode();

        if (!ServiceCallResult.isHttpSuccess(responseCode)) {
            return new ServiceCallResult(readErrorBody(connection), responseCode, connection.getResponseMessage());
        }

        /*
         * Lines are concatenated without a separator. That is lossless for JSON, which is what the services
         * return, since a JSON string cannot contain a raw line break.
         */
        try (var br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            var responseStr = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                responseStr.append(line.trim());
            }
            return new ServiceCallResult(responseStr.toString(), responseCode, connection.getResponseMessage());
        }
    }

}
