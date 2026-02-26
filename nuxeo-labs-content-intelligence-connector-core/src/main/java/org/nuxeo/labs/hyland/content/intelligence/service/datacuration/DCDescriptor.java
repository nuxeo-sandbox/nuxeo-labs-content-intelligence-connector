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
 *     Thibaud Arguillere
 */
package org.nuxeo.labs.hyland.content.intelligence.service.datacuration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.labs.hyland.content.intelligence.service.AbstractServiceDescriptor;

/**
 * @since 2023
 */
@XObject("dataCuration")
public class DCDescriptor extends AbstractServiceDescriptor {

    private static final Logger LOG = LogManager.getLogger(DCDescriptor.class);

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected String serviceLabel() {
        return "Data Curation";
    }
    
    @Override
    protected boolean requiresEnvironment() {
        return false;
    }
    
    @Override
    public String getEnvironment() {
        return null;
    }

}
