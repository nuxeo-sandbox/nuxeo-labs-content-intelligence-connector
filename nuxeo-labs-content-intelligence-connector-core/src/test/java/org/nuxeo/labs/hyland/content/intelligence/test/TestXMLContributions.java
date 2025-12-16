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
import org.nuxeo.labs.hyland.content.intelligence.service.discovery.HylandKDService;
import org.nuxeo.labs.hyland.content.intelligence.service.discovery.KDDescriptor;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.DCDescriptor;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.HylandKEService;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.KEDescriptor;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import jakarta.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class, ConfigCheckerFeature.class })
@Deploy("nuxeo-hyland-content-intelligence-connector-core")
public class TestXMLContributions {

    @Inject
    protected HylandKEService hylandKEService;

    @Inject
    protected HylandKDService hylandKDService;

    @Test
    public void testServiceIsDeployed() {
        assertNotNull(hylandKDService);
        assertNotNull(hylandKEService);
    }

    @Test
    public void shouldHaveDefaultKDConfig() {
        
        List<String> contribs = hylandKDService.getKDContribNames();
        assertNotNull(contribs);
        assertEquals(1, contribs.size());
        assertTrue(contribs.indexOf("default") == 0);
        
    }
    
    @Test
    public void defaultKDContribLooksOK() {
        
        KDDescriptor desc = hylandKDService.getKDDescriptor("default");
        assertNotNull(desc);
        
        assertTrue(desc.hasAllValues());
        
    }
    
    @Test
    @Deploy("nuxeo-hyland-content-intelligence-connector-core:more-mock-configs.xml")
    public void shouldDeployExtraContribs() {
        // Contribs contain random string => do not test connection to services, just existence.
        
        List<String> contribs = hylandKDService.getKDContribNames();
        assertEquals(2, contribs.size());
        KDDescriptor kdDesc = hylandKDService.getKDDescriptor("more-kd-1");
        assertNotNull(kdDesc);
        assertTrue(kdDesc.hasAllValues());

        
        contribs = hylandKEService.getKEContribNames();
        assertEquals(2, contribs.size());
        KEDescriptor keDesc = hylandKEService.getKEDescriptor("more-ke-1");
        assertNotNull(keDesc);
        assertTrue(keDesc.hasAllValues());
        

        contribs = hylandKEService.getDCContribNames();
        assertEquals(2, contribs.size());
        DCDescriptor dcDesc = hylandKEService.getDCDescriptor("more-dc-1");
        assertNotNull(dcDesc);
        assertTrue(dcDesc.hasAllValues());
    }
    
}
