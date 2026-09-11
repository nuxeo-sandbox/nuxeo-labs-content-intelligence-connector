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
package org.nuxeo.labs.hyland.content.intelligence.service.discovery;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.labs.hyland.content.intelligence.service.AbstractServiceDescriptor;

/**
 * @since 2023
 */
@XObject("knowledgeDiscovery")
public class KDDescriptor extends AbstractServiceDescriptor {

    @XNode("environment")
    protected String environment;

    /**
     * Merges the {@code environment} field, then chains to the common ones.
     *
     * @since 2025.20
     */
    @Override
    public void merge(AbstractServiceDescriptor other) {

        super.merge(other);

        if (other instanceof KDDescriptor otherDesc && StringUtils.isNotBlank(otherDesc.environment)) {
            environment = otherDesc.environment;
        }
    }

    /**
     * Knowledge Discovery does need an environment: it is sent as the {@code Hxp-Environment} header on every
     * call, and as {@code hxp-environment} on the authentication request.
     * <p>
     * This returned {@code false} until 2025.22, so an incomplete configuration raised no error at startup and
     * only failed at call time — while {@code AuthenticationToken.checkConfigOrThrow} did require the value.
     */
    @Override
    protected boolean requiresEnvironment() {
        return true;
    }

    @Override
    public String getEnvironment() {
        return environment;
    }
}
