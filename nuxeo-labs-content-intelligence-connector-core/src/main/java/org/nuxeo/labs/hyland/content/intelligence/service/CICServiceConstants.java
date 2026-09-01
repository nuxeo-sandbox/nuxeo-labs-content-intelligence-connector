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