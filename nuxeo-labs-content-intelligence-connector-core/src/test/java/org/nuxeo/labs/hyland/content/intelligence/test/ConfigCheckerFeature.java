/*
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
 *     Michael Vachette
 *     Thibaud Arguillere
 */
package org.nuxeo.labs.hyland.content.intelligence.test;

import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.labs.hyland.content.intelligence.service.CICServiceConstants;
import org.nuxeo.labs.hyland.content.intelligence.service.agents.HylandAgentsServiceImpl;
import org.nuxeo.labs.hyland.content.intelligence.service.datacuration.HylandDCServiceImpl;
import org.nuxeo.labs.hyland.content.intelligence.service.discovery.HylandKDServiceImpl;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.HylandKEServiceImpl;
import org.nuxeo.labs.hyland.content.intelligence.service.ingest.IngestServiceImpl;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RunnerFeature;

/**
 * Check the misc expected config parameters are set so a call to Hyland Content Intelligence
 * can be made.
 * also checks for environment variables and convert them to config. parameters, this may be useful
 * when testing quickly, so you can set the following variables:
 * <br>
 * ENRICHMENT:
 * CIC_AUTH_BASE_URL => nuxeo.hyland.cic.auth.baseUrl
 * CIC_ENRICHMENT_BASE_URL => nuxeo.hyland.cic.contextEnrichment.baseUrl
 * CIC_ENRICHMENT_CLIENT_ID => nuxeo.hyland.cic.enrichment.clientId
 * CIC_ENRICHMENT_CLIENT_SECRET => nuxeo.hyland.cic.enrichment.clientSecret
 * <br/>
 * DATA CURATION:
 * CIC_DATA_CURATION_CLIENT_ID => nuxeo.hyland.cic.datacuration.clientId
 * CIC_DATA_CURATION_CLIENT_SECRET => nuxeo.hyland.cic.datacuration.clientSecret
 * CIC_DATA_CURATION_BASE_URL => nuxeo.hyland.cic.dataCuration.baseUrl
 * <br/>
 * DISCOVERY:
 * CIC_DISCOVERY_BASE_URL
 * CIC_DISCOVERY_CLIENT_ID
 * CIC_DISCOVERY_CLIENT_SECRET
 * CIC_DISCOVERY_ENVIRONMENT
 * <br/>
 * AGENT BUILDER
 * CIC_AGENTS_BASE_URL
 * CIC_AGENTS_CLIENT_ID
 * CIC_AGENTS_CLIENT_SECRET
 * <br/>
 * INGESTION:
 * CIC_INGEST_BASE_URL
 * CIC_INGEST_CLIENT_ID
 * CIC_INGEST_CLIENT_SECRET
 * CIC_INGEST_ENVIRONMENT
 * <br/>
 * These are for unit test only (no config params)
 * CIC_DISCOVERY_UNIT_TEST_AGENT_ID
 * CIC_DISCOVERY_UNIT_TEST_REPO_SOURCE
 * CIC_AGENT_FOR_UNIT_TEST
 * CIC_AGENT_KD_RAG_UNIT_TEST_AGENT_ID
 * CIC_INGEST_UNIT_TEST_REPO_SOURCE
 * 
 * @since 2023
 */
public class ConfigCheckerFeature implements RunnerFeature {

    // ==========> Auth
    public static final String ENV_CIC_AUTH_BASE_URL = "CIC_AUTH_BASE_URL";

    // ==========> Content Enrichment
    public static final String ENV_CIC_ENRICHMENT_BASE_URL = "CIC_ENRICHMENT_BASE_URL";

    public static final String ENV_CIC_ENRICHMENT_CLIENT_ID = "CIC_ENRICHMENT_CLIENT_ID";

    public static final String ENV_CIC_ENRICHMENT_CLIENT_SECRET = "CIC_ENRICHMENT_CLIENT_SECRET";

    // ==========> Data Curation
    public static final String ENV_CIC_DATA_CURATION_BASE_URL = "CIC_DATA_CURATION_BASE_URL";

    public static final String ENV_CIC_DATA_CURATION_CLIENT_ID = "CIC_DATA_CURATION_CLIENT_ID";

    public static final String ENV_CIC_DATA_CURATION_CLIENT_SECRET = "CIC_DATA_CURATION_CLIENT_SECRET";

    // ==========> Discovery
    public static final String ENV_CIC_DISCOVERY_BASE_URL = "CIC_DISCOVERY_BASE_URL";

    public static final String ENV_CIC_DISCOVERY_CLIENT_ID = "CIC_DISCOVERY_CLIENT_ID";

    public static final String ENV_CIC_DISCOVERY_CLIENT_SECRET = "CIC_DISCOVERY_CLIENT_SECRET";

    public static final String ENV_CIC_DISCOVERY_ENVIRONMENT = "CIC_DISCOVERY_ENVIRONMENT";

    // ==========> Agents (Agent Builder)
    public static final String ENV_CIC_AGENTS_BASE_URL = "CIC_AGENTS_BASE_URL";

    public static final String ENV_CIC_AGENTS_CLIENT_ID = "CIC_AGENTS_CLIENT_ID";

    public static final String ENV_CIC_AGENTS_CLIENT_SECRET = "CIC_AGENTS_CLIENT_SECRET";

    // ==========> Content Lake
    public static final String ENV_CIC_INGEST_BASE_URL = "CIC_INGEST_BASE_URL";

    public static final String ENV_CIC_INGEST_CLIENT_ID = "CIC_INGEST_CLIENT_ID";

    public static final String ENV_CIC_INGEST_CLIENT_SECRET = "CIC_INGEST_CLIENT_SECRET";

    public static final String ENV_CIC_INGEST_ENVIRONMENT = "CIC_INGEST_ENVIRONMENT";

    // ==========> Other, not config params
    public static final String ENV_CIC_DISCOVERY_UNIT_TEST_AGENT_ID = "CIC_DISCOVERY_UNIT_TEST_AGENT_ID";

    public static final String ENV_CIC_DISCOVERY_UNIT_TEST_REPO_SOURCE = "CIC_DISCOVERY_UNIT_TEST_REPO_SOURCE";

    public static final String ENV_CIC_AGENT_FOR_UNIT_TEST = "CIC_AGENT_FOR_UNIT_TEST";

    public static final String ENV_CIC_AGENT_KD_RAG_UNIT_TEST_AGENT_ID = "CIC_AGENT_KD_RAG_UNIT_TEST_AGENT_ID";

    public static final String ENV_CIC_INGEST_UNIT_TEST_REPO_SOURCE = "CIC_INGEST_UNIT_TEST_REPO_SOURCE";

    // ==========
    protected static boolean hasEnrichmentClientInfo = false;

    protected static boolean hasDataCurationClientInfo = false;

    protected static boolean hasDiscoveryClientInfo = false;

    protected static boolean hasAgentsClientInfo = false;

    protected static boolean hasIngestClientInfo = false;

    Properties systemProps = null;

    public static boolean hasEnrichmentClientInfo() {
        return hasEnrichmentClientInfo;
    }

    public static boolean hasDataCurationClientInfo() {
        return hasDataCurationClientInfo;
    }

    public static boolean hasDiscoveryClientInfo() {
        return hasDiscoveryClientInfo;
    }

    public static boolean hasAgentsClientInfo() {
        return hasAgentsClientInfo;
    }
    
    public static boolean hasIngestClientInfo() {
        return hasIngestClientInfo;
    }

    @Override
    public void initialize(FeaturesRunner runner) throws Exception {

        systemProps = System.getProperties();

        boolean hasEndpointAuth = hasProperty(CICServiceConstants.AUTH_BASE_URL_PARAM, ENV_CIC_AUTH_BASE_URL);
        if (!hasEndpointAuth) {
            System.out.println("Missing CIC Auth endpoint => no tests");
        }

        boolean hasEnrichmentBaseUrl = hasProperty(HylandKEServiceImpl.CONTEXT_ENRICHMENT_BASE_URL_PARAM,
                ENV_CIC_ENRICHMENT_BASE_URL);
        if (!hasEnrichmentBaseUrl) {
            System.out.println("Missing CIC Enrichment Base URL => no enrichment tests");
        }
        boolean hasEnrichmentClientId = hasProperty(HylandKEServiceImpl.ENRICHMENT_CLIENT_ID_PARAM,
                ENV_CIC_ENRICHMENT_CLIENT_ID);
        boolean hasEnrichmentClientSecret = hasProperty(HylandKEServiceImpl.ENRICHMENT_CLIENT_SECRET_PARAM,
                ENV_CIC_ENRICHMENT_CLIENT_SECRET);
        hasEnrichmentClientInfo = hasEndpointAuth && hasEnrichmentBaseUrl && hasEnrichmentClientId
                && hasEnrichmentClientSecret;
        if (!hasEnrichmentClientInfo) {
            System.out.println("Missing CIC Enrichment Client info => no tests of enricvhment");
        }

        boolean hasDataCurationBaseUrl = hasProperty(HylandDCServiceImpl.DATA_CURATION_BASE_URL_PARAM,
                ENV_CIC_DATA_CURATION_BASE_URL);
        if (!hasDataCurationBaseUrl) {
            System.out.println("Missing CIC Data Curation Base URL => no data curation tests");
        }
        boolean hasDataCurationClientId = hasProperty(HylandDCServiceImpl.DATA_CURATION_CLIENT_ID_PARAM,
                ENV_CIC_DATA_CURATION_CLIENT_ID);
        boolean hasDataCurationClientSecret = hasProperty(HylandDCServiceImpl.DATA_CURATION_CLIENT_SECRET_PARAM,
                ENV_CIC_DATA_CURATION_CLIENT_SECRET);
        hasDataCurationClientInfo = hasEndpointAuth && hasDataCurationBaseUrl && hasDataCurationClientId
                && hasDataCurationClientSecret;
        if (!hasDataCurationClientInfo) {
            System.out.println("Missing CIC Data Curation Client info => no tests of data curation");
        }

        boolean hasDiscoveryBaseUrl = hasProperty(HylandKDServiceImpl.DISCOVERY_BASE_URL_PARAM,
                ENV_CIC_DISCOVERY_BASE_URL);
        if (!hasDiscoveryBaseUrl) {
            System.out.println("Missing CIC Discovery Base URL => no discovery tests");
        }
        boolean hasDiscoveryClientId = hasProperty(HylandKDServiceImpl.DISCOVERY_CLIENT_ID_PARAM,
                ENV_CIC_DISCOVERY_CLIENT_ID);
        boolean hasDiscoveryClientSecret = hasProperty(HylandKDServiceImpl.DISCOVERY_CLIENT_SECRET_PARAM,
                ENV_CIC_DISCOVERY_CLIENT_SECRET);
        boolean hasDiscoveryEnvironment = hasProperty(HylandKDServiceImpl.DISCOVERY_ENVIRONMENT_PARAM,
                ENV_CIC_DISCOVERY_ENVIRONMENT);
        hasDiscoveryClientInfo = hasEndpointAuth && hasDiscoveryClientId && hasDiscoveryClientSecret
                && hasDiscoveryEnvironment;
        if (!hasDiscoveryClientInfo) {
            System.out.println("Missing CIC Discovery Client info => no tests of Discovery");
        }

        boolean hasAgentsBaseUrl = hasProperty(HylandAgentsServiceImpl.AGENTS_BASE_URL_PARAM, ENV_CIC_AGENTS_BASE_URL);
        if (!hasAgentsBaseUrl) {
            System.out.println("Missing CIC Agents Base URL => no Agent tests");
        }
        boolean hasAgentsClientId = hasProperty(HylandAgentsServiceImpl.AGENTS_CLIENT_ID_PARAM,
                ENV_CIC_DISCOVERY_CLIENT_ID);
        boolean hasAgentsClientSecret = hasProperty(HylandAgentsServiceImpl.AGENTS_CLIENT_SECRET_PARAM,
                ENV_CIC_DISCOVERY_CLIENT_SECRET);
        hasAgentsClientInfo = hasEndpointAuth && hasAgentsClientId && hasAgentsClientSecret;
        if (!hasAgentsClientInfo) {
            System.out.println("Missing CIC Agents Client info => no tests of agents");
        }
        

        boolean hasIngestBaseUrl = hasProperty(IngestServiceImpl.INGEST_BASE_URL_PARAM,
                ENV_CIC_INGEST_BASE_URL);
        if (!hasIngestBaseUrl) {
            System.out.println("Missing CIC Content Lake Base URL => no Content Lake tests");
        }
        boolean hasIngestClientId = hasProperty(IngestServiceImpl.INGEST_CLIENT_ID_PARAM,
                ENV_CIC_INGEST_CLIENT_ID);
        boolean hasIngestClientSecret = hasProperty(IngestServiceImpl.INGEST_CLIENT_SECRET_PARAM,
                ENV_CIC_INGEST_CLIENT_SECRET);
        boolean hasIngestEnvironment = hasProperty(IngestServiceImpl.INGEST_ENVIRONMENT_PARAM,
                ENV_CIC_INGEST_ENVIRONMENT);
        hasIngestClientInfo = hasEndpointAuth && hasIngestClientId && hasIngestClientSecret
                && hasIngestEnvironment;
        if (!hasIngestClientInfo) {
            System.out.println("Missing CIC Content Lake Client info => no tests of COntent Lake");
        }

    }

    protected boolean hasProperty(String property, String envVar) {

        String value = systemProps.getProperty(property);

        if (StringUtils.isBlank(value)) {
            value = System.getenv(envVar);
            if (!StringUtils.isBlank(value)) {
                systemProps.put(property, value);
                return true;
            }
        }

        return false;
    }

}
