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
 *     Thibaud Arguillere
 */
package org.nuxeo.labs.hyland.content.intelligence.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
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
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import jakarta.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class, ConfigCheckerFeature.class })
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
    
    @Test
    public void defaultKDContribLooksOK() {
        
        KDDescriptor desc = kdService.getKDDescriptor("default");
        assertNotNull(desc);
        
        assertTrue(desc.hasAllValues());
        
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
        assertEquals(2, contribs.size());
        KEDescriptor keDesc = keService.getKEDescriptor("more-ke-1");
        assertNotNull(keDesc);
        assertTrue(keDesc.hasAllValues());
        

        contribs = dcService.getContribNames();
        assertEquals(2, contribs.size());
        DCDescriptor dcDesc = dcService.getDCDescriptor("more-dc-1");
        assertNotNull(dcDesc);
        assertTrue(dcDesc.hasAllValues());
    }
    
}
