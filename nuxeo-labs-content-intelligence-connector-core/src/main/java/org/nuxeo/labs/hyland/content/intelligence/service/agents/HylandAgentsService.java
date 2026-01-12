/*
 * (C) Copyright 2026 Hyland (http://hyland.com/)  and others.
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
package org.nuxeo.labs.hyland.content.intelligence.service.agents;

import java.util.List;
import java.util.Map;

import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;

/**
 * 
 * @since TODO
 */
public interface HylandAgentsService {
    
    /** Get all agents in the account/app. referenced by configName.
     * 
     * configName is the contribution to read for authentication and misc. If null or "", we use "default" (contributed
     * by the plugin and using config. parameters)
     * 
     * @param configName
     * @param extraHeaders, optional
     * @return
     * @since TODO
     */
    public ServiceCallResult getAllAgents(String configName, Map<String, String> extraHeaders);
    
    /**
     * returns the full JSON after calling the service.
     * 
     * If versionId is null or empty, the latest version is returned.
     * 
     * configName is the contribution to read for authentication and misc. If null or "", we use "default" (contributed
     * by the plugin and using config. parameters)
     * 
     * @param configName
     * @param agentId
     * @param versionId
     * @param extraHeaders
     * @return
     * @since TODO
     */
    public ServiceCallResult lookupAgent(String configName, String agentId, String versionId, Map<String, String> extraHeaders);
    
    /**
     * Invoke the task agent agentId. Pass in payloadJsonStr the expected input for the agent.
     * 
     * If versionId is null or empty, the latest version is invoked.
     * 
     * @param configName, optional
     * @param agentId, required
     * @param versionId, optional
     * @param payloadJsonStr, required
     * @param extraHeaders, optional
     * @return
     * @since TODO
     */
    public ServiceCallResult invokeTask(String configName, String agentId, String versionId, String payloadJsonStr, Map<String, String> extraHeaders);

    /**
     * 
     * @return the list of contributions
     * @since 2023
     */
    List<String> getAgentContribNames();
    
    /**
     * Introspection
     */
    AgentDescriptor getAgentDescriptor(String configName);

}
