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
package org.nuxeo.labs.hyland.content.intelligence.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.directory.test.DirectoryFeature;
import org.nuxeo.ecm.directory.Directory;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.labs.hyland.content.intelligence.service.CICServiceConstants;
import org.nuxeo.labs.hyland.content.intelligence.service.agents.HylandAgentsService;
import org.nuxeo.labs.hyland.content.intelligence.service.contentlake.ContentLakeService;
import org.nuxeo.labs.hyland.content.intelligence.service.datacuration.DCDescriptor;
import org.nuxeo.labs.hyland.content.intelligence.service.datacuration.HylandDCService;
import org.nuxeo.labs.hyland.content.intelligence.service.discovery.HylandKDService;
import org.nuxeo.labs.hyland.content.intelligence.service.discovery.KDDescriptor;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.HylandKEService;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.KEDescriptor;
import org.nuxeo.labs.hyland.content.intelligence.service.ingest.IngestService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import jakarta.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class, DirectoryFeature.class, ConfigCheckerFeature.class })
@Deploy("nuxeo-hyland-content-intelligence-connector-core")
public class TestXMLContributions {

    @Inject
    protected HylandKEService keService;

    @Inject
    protected HylandDCService dcService;

    @Inject
    protected HylandKDService kdService;

    @Inject
    protected HylandAgentsService agentsService;

    @Inject
    protected IngestService ingestService;

    @Inject
    protected ContentLakeService clService;

    @Test
    public void testServicesAreDeployed() {
        assertNotNull(keService);
        assertNotNull(dcService);
        assertNotNull(kdService);
        assertNotNull(agentsService);
        assertNotNull(ingestService);
        assertNotNull(clService);
    }

    protected void checkHasDefaultContrib(List<String> contribs) {
        assertNotNull(contribs);
        assertEquals(1, contribs.size());
        assertTrue(contribs.indexOf(CICServiceConstants.CONFIG_DEFAULT) == 0);
    }

    @Test
    public void shouldHaveDefaultConfigs() {

        checkHasDefaultContrib(keService.getContribNames());
        checkHasDefaultContrib(dcService.getContribNames());
        checkHasDefaultContrib(kdService.getContribNames());
        checkHasDefaultContrib(agentsService.getContribNames());
        checkHasDefaultContrib(ingestService.getContribNames());
        checkHasDefaultContrib(clService.getContribNames());

    }

    /**
     * The parts of the KD contribution that hold offline are checked unconditionally; {@code hasAllValues()} is
     * only asserted when the credentials are actually configured.
     * <p>
     * This test used to assert {@code hasAllValues()} outright, so it failed — rather than skipped — on any
     * machine without the {@code CIC_DISCOVERY_*} environment variables, since {@code clientId} and friends then
     * resolve to the empty string. {@code tokenGrantType} and {@code tokenScope}, on the other hand, can never be
     * blank: their {@code ${...:=}} defaults in the XML are non-empty.
     */
    @Test
    public void defaultKDContribLooksOK() {

        KDDescriptor desc = kdService.getKDDescriptor("default");
        assertNotNull(desc);

        assertTrue(StringUtils.isNotBlank(desc.getAuthenticationTokenParams().getGrantType()));
        assertTrue(StringUtils.isNotBlank(desc.getAuthenticationTokenParams().getGrantScope()));

        if (ConfigCheckerFeature.hasDiscoveryClientInfo()) {
            assertTrue("The Discovery credentials are configured, the contribution should be complete",
                    desc.hasAllValues());
        }
    }

    @Test
    @Deploy("nuxeo-hyland-content-intelligence-connector-core:more-mock-configs.xml")
    public void shouldDeployExtraContribs() {
        // Contribs contain random string => do not test connection to services, just existence.

        List<String> contribs = kdService.getContribNames();
        assertEquals(2, contribs.size());
        KDDescriptor kdDesc = kdService.getKDDescriptor("more-kd-1");
        assertNotNull(kdDesc);
        assertTrue(kdDesc.hasAllValues());


        contribs = keService.getContribNames();
        // default + more-ke-1 + more-ke-with-embeddings
        assertEquals(3, contribs.size());
        KEDescriptor keDesc = keService.getKEDescriptor("more-ke-1");
        assertNotNull(keDesc);
        assertTrue(keDesc.hasAllValues());


        contribs = dcService.getContribNames();
        assertEquals(2, contribs.size());
        DCDescriptor dcDesc = dcService.getDCDescriptor("more-dc-1");
        assertNotNull(dcDesc);
        assertTrue(dcDesc.hasAllValues());
    }

    /**
     * The two classification vocabularies must actually open and be populated.
     * <p>
     * They were contributed to {@code SQLDirectoryFactory} with a hardcoded {@code java:/nxsqldirectory}
     * datasource until 2025.22, which is bound only by the {@code common-sql} server template: on any MongoDB
     * deployment both directories were unusable, the classification operations fell back to an empty candidate
     * list and the {@code cic-classification} form widgets stayed empty. They now extend
     * {@code template-vocabulary} through {@code GenericDirectory}, which is backend agnostic.
     *
     * @since 2025.22
     */
    @Test
    @Deploy("nuxeo-hyland-content-intelligence-connector-core:test-vocabulary-template-contrib.xml")
    public void classificationVocabulariesShouldBeUsable() {

        DirectoryService ds = Framework.getService(DirectoryService.class);
        assertNotNull(ds);

        for (String name : new String[] { "cicImageClassification", "cicTextClassification" }) {
            Directory directory = ds.getDirectory(name);
            assertNotNull("Directory " + name + " is not registered", directory);
            try (Session dirSession = ds.open(name)) {
                assertFalse("Directory " + name + " is empty, the CSV was not loaded",
                        dirSession.query(Map.of(), Set.of()).isEmpty());
            }
        }
    }

    /**
     * Verify the optional embeddings* descriptor fields parse and are exposed via the service.
     *
     * @since 2025.18
     */
    @Test
    @Deploy("nuxeo-hyland-content-intelligence-connector-core:more-mock-configs.xml")
    public void embeddingsDescriptorFieldsParse() {
        // default contrib leaves them blank
        KEDescriptor defaultDesc = keService.getKEDescriptor(CICServiceConstants.CONFIG_DEFAULT);
        assertNotNull(defaultDesc);
        assertNull(defaultDesc.getEmbeddingsFacet());
        assertNull(defaultDesc.getEmbeddingsImageXpath());
        assertNull(defaultDesc.getEmbeddingsTextXpath());
        assertNull(keService.getEmbeddingsFacet(CICServiceConstants.CONFIG_DEFAULT));

        // more-ke-with-embeddings contrib sets them
        KEDescriptor desc = keService.getKEDescriptor("more-ke-with-embeddings");
        assertNotNull(desc);
        assertEquals("Embeddings", desc.getEmbeddingsFacet());
        assertEquals("embeddings:image", desc.getEmbeddingsImageXpath());
        assertEquals("embeddings:text", desc.getEmbeddingsTextXpath());

        // service convenience getters
        assertEquals("Embeddings", keService.getEmbeddingsFacet("more-ke-with-embeddings"));
        assertEquals("embeddings:image", keService.getEmbeddingsImageXpath("more-ke-with-embeddings"));
        assertEquals("embeddings:text", keService.getEmbeddingsTextXpath("more-ke-with-embeddings"));
    }

}
