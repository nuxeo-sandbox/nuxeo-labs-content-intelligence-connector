/*
 * (C) Copyright 2026 Hyland (http://hyland.com/) and others.
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

public final class CICServiceConstants {

    public static final String CONFIG_DEFAULT = "default";

    public static final String AUTH_BASE_URL_PARAM = "nuxeo.hyland.cic.auth.baseUrl";

    public static final String AUTH_ENDPOINT = "/connect/token";

    /**
     * Global, service-independent flag. When {@code true}, every call to Content Intelligence is logged at INFO
     * level (see {@link ServicesUtils#logCICCall}). Unlike all the other configuration parameters, this one is NOT
     * tied to a specific service family nor to a named contribution: it applies to the whole plugin.
     *
     * @since 2025.20
     */
    public static final String MORE_LOGS_PARAM = "nuxeo.hyland.cic.moreLogs";

    /**
     * Default value for {@link #MORE_LOGS_PARAM}.
     *
     * @since 2025.20
     */
    public static final boolean MORE_LOGS_DEFAULT = false;

    /**
     * Global, service-independent connect timeout, in milliseconds, applied to every HTTP call to Content
     * Intelligence. {@code 0} means "wait forever", which is what the plugin did before 2025.22.
     * <p>
     * An unresponsive endpoint used to block the calling thread indefinitely. That is particularly damaging for
     * the {@code cicEnrichment} Work queue, whose default {@code maxThreads} is 1: a single hung call froze all
     * asynchronous enrichment, with nothing in the logs to explain it.
     *
     * @since 2025.22
     */
    public static final String HTTP_CONNECT_TIMEOUT_PARAM = "nuxeo.hyland.cic.http.connectTimeout";

    /**
     * Default value for {@link #HTTP_CONNECT_TIMEOUT_PARAM}: 30 seconds.
     *
     * @since 2025.22
     */
    public static final int HTTP_CONNECT_TIMEOUT_DEFAULT = 30000;

    /**
     * Global, service-independent read timeout, in milliseconds, applied to every HTTP call to Content
     * Intelligence. {@code 0} means "wait forever".
     * <p>
     * The default is deliberately generous: some Content Intelligence endpoints legitimately take a long time to
     * answer. It only needs to be low enough that a dead connection is eventually released.
     *
     * @since 2025.22
     */
    public static final String HTTP_READ_TIMEOUT_PARAM = "nuxeo.hyland.cic.http.readTimeout";

    /**
     * Default value for {@link #HTTP_READ_TIMEOUT_PARAM}: 120 seconds.
     *
     * @since 2025.22
     */
    public static final int HTTP_READ_TIMEOUT_DEFAULT = 120000;

    /*
     * Short service codes used ONLY when building the "Calling CIC <service>/<action>" log messages. They are
     * deliberately distinct from the per-service SERVICE_LABEL constants (e.g. HylandKEService.SERVICE_LABEL is the
     * long "Knowledge Enrichment" label, used in CICError messages), which would make the logs verbose.
     */

    /** @since 2025.20 */
    public static final String SERVICE_CODE_KE = "KE";

    /** @since 2025.20 */
    public static final String SERVICE_CODE_DC = "DC";

    /** @since 2025.20 */
    public static final String SERVICE_CODE_KD = "KD";

    /** @since 2025.20 */
    public static final String SERVICE_CODE_AGENTS = "Agents";

    /** @since 2025.20 */
    public static final String SERVICE_CODE_INGEST = "Ingest";

    /** @since 2025.20 */
    public static final String SERVICE_CODE_CONTENTLAKE = "ContentLake";

    private CICServiceConstants() {
    }
}
