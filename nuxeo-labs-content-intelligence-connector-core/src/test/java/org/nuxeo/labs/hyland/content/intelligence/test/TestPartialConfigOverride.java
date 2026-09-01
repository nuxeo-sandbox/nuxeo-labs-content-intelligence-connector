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
package org.nuxeo.labs.hyland.content.intelligence.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.labs.hyland.content.intelligence.authentication.AuthenticationTokenEnrichment;
import org.nuxeo.labs.hyland.content.intelligence.authentication.AuthenticationTokenParams;
import org.nuxeo.labs.hyland.content.intelligence.service.CICServiceConstants;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.HylandKEService;
import org.nuxeo.labs.hyland.content.intelligence.service.enrichment.KEDescriptor;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import jakarta.inject.Inject;

/**
 * Contributing a configuration whose name already exists must MERGE, not replace.
 * <p>
 * Regression test for the authentication bug where a Studio project contributed
 * {@code <name>default</name>} with only the {@code embeddings*} fields, which wiped the credentials of the
 * plugin's own contribution and made {@code URLEncoder.encode} throw a bare NPE at enrichment time.
 * <p>
 * These tests need NO CIC credentials: they assert on {@code tokenGrantType} / {@code tokenScope}, whose values
 * come from the XML defaults ({@code ${...:=client_credentials}}) and are therefore never blank, whatever the
 * environment variables.
 *
 * @since 2025.20
 */
@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class, ConfigCheckerFeature.class })
@Deploy("nuxeo-hyland-content-intelligence-connector-core")
public class TestPartialConfigOverride {

    @Inject
    protected HylandKEService keService;

    @Test
    @Deploy("nuxeo-hyland-content-intelligence-connector-core:partial-override-configs.xml")
    public void partialOverrideMustNotWipeExistingValues() {

        KEDescriptor desc = keService.getKEDescriptor(CICServiceConstants.CONFIG_DEFAULT);
        assertNotNull(desc);

        AuthenticationTokenParams params = desc.getAuthenticationTokenParams();

        // These come from the plugin's own contribution and must have survived the partial override
        assertEquals("client_credentials", params.getGrantType());
        assertEquals("environment_authorization", params.getGrantScope());
    }

    @Test
    @Deploy("nuxeo-hyland-content-intelligence-connector-core:partial-override-configs.xml")
    public void partialOverrideMustApplyItsOwnValues() {

        KEDescriptor desc = keService.getKEDescriptor(CICServiceConstants.CONFIG_DEFAULT);
        assertNotNull(desc);

        // Added by the first partial contribution
        assertEquals("Embeddings", desc.getEmbeddingsFacet());
        assertEquals("embeddings:image", desc.getEmbeddingsImageXpath());
        assertEquals("embeddings:text", desc.getEmbeddingsTextXpath());

        // Added by the second one
        assertEquals("Medium", desc.getPictureRenditionName());
    }

    @Test
    @Deploy("nuxeo-hyland-content-intelligence-connector-core:partial-override-configs.xml")
    public void overridingMustNotCreateANewConfiguration() {

        List<String> contribs = keService.getContribNames();
        assertEquals(1, contribs.size());
        assertEquals(CICServiceConstants.CONFIG_DEFAULT, contribs.get(0));
    }

    /**
     * An explicitly empty value, which is what {@code ${some.undefined.param:=}} resolves to, must not erase an
     * existing value either.
     */
    @Test
    @Deploy("nuxeo-hyland-content-intelligence-connector-core:partial-override-configs.xml")
    public void emptyValueMustNotWipeExistingValue() {

        KEDescriptor desc = keService.getKEDescriptor(CICServiceConstants.CONFIG_DEFAULT);
        assertNotNull(desc);

        // partial-override-configs.xml declares <tokenScope></tokenScope>
        assertEquals("environment_authorization", desc.getAuthenticationTokenParams().getGrantScope());
    }

    /**
     * An unusable configuration must fail fast with an explicit message, not with a NullPointerException deep
     * inside URLEncoder.
     */
    @Test
    public void incompleteConfigMustThrowExplicitError() {

        AuthenticationTokenParams params = new AuthenticationTokenParams("client_credentials",
                "environment_authorization", null, null, null);
        AuthenticationTokenEnrichment token = new AuthenticationTokenEnrichment("https://example.com/idp/connect/token",
                params, "default");

        try {
            token.getToken();
            fail("Getting a token with no clientId/clientSecret should have thrown");
        } catch (NuxeoException e) {
            String msg = e.getMessage();
            assertTrue(msg, msg.contains("clientId"));
            assertTrue(msg, msg.contains("clientSecret"));
            assertTrue(msg, msg.contains("default"));
        }
    }
}
