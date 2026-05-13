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
package org.nuxeo.labs.hyland.content.intelligence.automation.enrichment;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.HylandKEService;

/**
 * Configure runtime tuning of the Knowledge Enrichment service.
 *
 * @since 2025.16 the {@code useKEV2} parameter is ignored: the plugin always uses Knowledge Enrichment v2
 *        because v1 is deprecated and will be removed. Passing {@code useKEV2=false} logs a WARN.
 */
@Operation(id = ConfigureServiceOp.ID, category = "Hyland Knowledge Enrichment", label = "Configure Calls to Service", description = ""
        + "Allows for dynamically changing some settings when calling the service."
        + " maxTries and sleepIntervalMS: if a value is 0 => reset to configuration or default value."
        + " If -1 (or not passed) => do not change."
        + " useKEV2 is deprecated and ignored since plugin version 2025.16: the plugin always uses"
        + " Knowledge Enrichment v2 because v1 is deprecated and will be removed."
        + " Passing useKEV2=false will be ignored and a WARN will be logged.")
public class ConfigureServiceOp {

    public static final String ID = "HylandKnowledgeEnrichment.Configure";

    private static final Logger log = LogManager.getLogger(ConfigureServiceOp.class);

    @Context
    protected HylandKEService keService;

    @Param(name = "maxTries", required = false)
    protected Integer maxTries = null;

    @Param(name = "sleepIntervalMS", required = false)
    protected Integer sleepIntervalMS = null;

    @Param(name = "useKEV2", required = false)
    protected Boolean useKEV2 = null;

    @OperationMethod
    public void run() {

        keService.setPullResultsSettings(maxTries == null ? 1 : maxTries, sleepIntervalMS == null ? -1 : sleepIntervalMS);

        // Since plugin version 2025.16, the plugin always uses KE v2 because v1 is deprecated
        // and will be removed. The useKEV2 parameter is kept for backward compatibility but is ignored.
        if (useKEV2 != null && !useKEV2.booleanValue()) {
            log.warn("The 'useKEV2' parameter of {} is deprecated and ignored since plugin version 2025.16."
                    + " The plugin always uses Knowledge Enrichment v2 because v1 is deprecated and will be removed."
                    + " Please remove this parameter from your call.", ID);
        }

    }

}
