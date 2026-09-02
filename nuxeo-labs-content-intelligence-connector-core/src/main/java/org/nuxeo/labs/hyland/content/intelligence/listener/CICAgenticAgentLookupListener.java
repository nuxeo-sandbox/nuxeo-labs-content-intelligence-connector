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
package org.nuxeo.labs.hyland.content.intelligence.listener;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.PostCommitEventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.event.impl.ShallowDocumentModel;
import org.nuxeo.labs.hyland.content.intelligence.automation.agents.CICAgenticAgentLookupOp;
import org.nuxeo.runtime.api.Framework;

/**
 * Post-commit, async listener that runs {@link CICAgenticAgentLookupOp} (with {@code saveDocument=true}) on every
 * {@code documentCreated} event for a {@code CICAgenticAgentAndConfig} document, so the agent's input schema and tools
 * are auto-populated right after creation.
 * <p>
 * Throws a {@link NuxeoException} if the document is created with a blank {@code cicagenticagentandconfig:agentId}.
 * Because the listener is post-commit + async, the throw does not roll back the creation; it is logged by the async
 * event machinery.
 *
 * @since 2025.18
 */
public class CICAgenticAgentLookupListener implements PostCommitEventListener {

    private static final Logger log = LogManager.getLogger(CICAgenticAgentLookupListener.class);

    @Override
    public void handleEvent(EventBundle events) {
        events.forEach(this::handleEvent);
    }

    protected void handleEvent(Event event) {
        EventContext ctx = event.getContext();
        if (!(ctx instanceof DocumentEventContext docCtx)) {
            return;
        }

        DocumentModel doc = docCtx.getSourceDocument();
        if (doc == null || doc instanceof ShallowDocumentModel) {
            return;
        }
        if (doc.isProxy() || doc.isVersion()) {
            return;
        }
        if (!CICAgenticAgentLookupOp.DOCTYPE.equals(doc.getType())) {
            return;
        }

        String agentId = (String) doc.getPropertyValue(CICAgenticAgentLookupOp.XPATH_AGENT_ID);
        if (agentId == null || agentId.isBlank()) {
            throw new NuxeoException(
                    "CICAgenticAgentAndConfig requires a non-blank agentId (doc id=" + doc.getId() + ")");
        }

        AutomationService automation = Framework.getService(AutomationService.class);
        try (OperationContext octx = new OperationContext(docCtx.getCoreSession())) {
            octx.setInput(doc);
            automation.run(octx, CICAgenticAgentLookupOp.ID, Map.of("saveDocument", true));
        } catch (OperationException e) {
            throw new NuxeoException("Failed to run " + CICAgenticAgentLookupOp.ID + " on doc id=" + doc.getId(), e);
        }
        log.debug("CIC.AgenticAgentLookup ran on doc id={}", doc.getId());
    }

}
