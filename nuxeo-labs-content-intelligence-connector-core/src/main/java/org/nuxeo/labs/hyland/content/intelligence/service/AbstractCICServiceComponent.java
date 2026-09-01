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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.labs.hyland.content.intelligence.authentication.AuthenticationToken;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.model.Extension;

/**
 * Adding more and more services (KE, then KE/DC, then KD, then Agents, then...)
 * => a good part of the code starting a component or giving access to its configurations etc. was very the same.
 * => The code is shared here, with a couple utilities to get info from children (like the extension point).
 * 
 * @param <D> the Descriptor class (KDDescriptor, KEDescriptor, ...)
 * @since 2025.15/2023.18
 */
public abstract class AbstractCICServiceComponent<D extends AbstractServiceDescriptor> extends DefaultComponent {

    protected Map<String, D> contribs = new HashMap<>();

    // ======================================================================
    // ======================================================================
    // Shared code for the services
    // ======================================================================
    // ======================================================================
    public String checkConfigName(String configName) {
        if (StringUtils.isBlank(configName)) {
            return CICServiceConstants.CONFIG_DEFAULT;
        }
        return configName;
    }

    public D getDescriptor(String configName) {
        return contribs.get(checkConfigName(configName));
    }

    public String getToken(Map<String, AuthenticationToken> tokens, String configName) {

        if (tokens == null) {
            return null;
        }

        AuthenticationToken token = tokens.get(checkConfigName(configName));
        if (token == null) {
            return null;
        }

        return token.getToken();
    }

    public List<String> getContribNames() {
        if(contribs == null) {
            contribs = new HashMap<>();
        }
        return new ArrayList<>(contribs.keySet());
    }

    public Map<String, D> getContribMap() {
        return contribs;
    }

    // ======================================================================
    // ======================================================================
    // DefaultComponent overrides ands utilities
    // ======================================================================
    // ======================================================================
    // Each child class must implement this method to specify the extension point for the descriptors
    // at register/unregister time.
    public abstract String getDescriptorExtensionPoint();

    public String getServiceLabel() {
        return this.getClass().getSimpleName();
    }

    //Called in every start() method of each service so they initialize their auth tokens map
    protected Map<String, AuthenticationToken> initAuthTokens(Function<D, AuthenticationToken> tokenFactory) {

        Logger log = LogManager.getLogger(this.getClass());

        if (getContribMap().isEmpty()) {
            log.error("No configuration found for {}. Calls, if any, will fail.", getServiceLabel());
            return null;
        }

        Map<String, AuthenticationToken> tokens = new HashMap<>();
        for (D desc : getContribMap().values()) {
            AuthenticationToken token = tokenFactory.apply(desc);
            tokens.put(desc.getName(), token);
            if (!desc.hasAllValues()) {
                /*
                 * One single, actionable ERROR instead of one WARN per missing field. The most frequent cause is an
                 * incomplete override of an existing configuration name, so we say it explicitly.
                 */
                log.error(
                        "Configuration '{}' of {} is incomplete, missing value(s): {}."
                                + " Calls using this configuration will fail."
                                + " If you contribute a configuration with an already existing name, note that only the"
                                + " fields you declare are overridden, the other ones are kept.",
                        desc.getName(), getServiceLabel(), desc.getMissingValuesAsString());
            }
        }

        return tokens;
    }

    // Internal, private, called by registerExtension(...)
    @SuppressWarnings("unchecked")
    private void registerDescriptorExtension(Extension extension, String extensionPoint) {
        if (!extensionPoint.equals(extension.getExtensionPoint())) {
            return;
        }
        
        if(contribs == null) {
            contribs = new HashMap<>();
        }

        Object[] contributions = extension.getContributions();
        if (contributions != null) {
            for (Object contribution : contributions) {
                D descriptor = (D) contribution;
                D existing = contribs.get(descriptor.getName());
                if (existing == null) {
                    contribs.put(descriptor.getName(), descriptor);
                } else {
                    /*
                     * A contribution with the same name already exists => MERGE it instead of replacing it, so a
                     * partial override (typically from a Studio project adding one or two fields) does not silently
                     * wipe the values it does not redeclare. Only non-blank values of the new contribution win.
                     * See AbstractServiceDescriptor#merge.
                     */
                    existing.merge(descriptor);
                }
            }
        }
    }

     // Internal, private, called by unregisterExtension(...)
    @SuppressWarnings("unchecked")
    private void unregisterDescriptorExtension(Extension extension, String extensionPoint) {
        if (!extensionPoint.equals(extension.getExtensionPoint())) {
            return;
        }
        
        if(contribs == null) {
            return;
        }

        Object[] contributions = extension.getContributions();
        if (contributions != null) {
            for (Object contribution : contributions) {
                D descriptor = (D) contribution;
                contribs.remove(descriptor.getName());
            }
        }
    }

    @Override
    public void registerExtension(Extension extension) {
        super.registerExtension(extension);
        registerDescriptorExtension(extension, getDescriptorExtensionPoint());
    }

    @Override
    public void unregisterExtension(Extension extension) {
        super.unregisterExtension(extension);
        unregisterDescriptorExtension(extension, getDescriptorExtensionPoint());
    }

    /**
     * Component activated notification.
     * Called when the component is activated. All component dependencies are resolved at that moment.
     * Use this method to initialize the component.
     *
     * @param context the component context.
     */
    @Override
    public void activate(ComponentContext context) {
        super.activate(context);
    }

    /**
     * Component deactivated notification.
     * Called before a component is unregistered.
     * Use this method to do cleanup if any and free any resources held by the component.
     *
     * @param context the component context.
     */
    @Override
    public void deactivate(ComponentContext context) {
        super.deactivate(context);
    }
}