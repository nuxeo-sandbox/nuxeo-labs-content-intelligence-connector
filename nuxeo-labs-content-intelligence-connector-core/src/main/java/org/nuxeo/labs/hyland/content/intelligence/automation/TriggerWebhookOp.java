package org.nuxeo.labs.hyland.content.intelligence.automation;

import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.labs.hyland.content.intelligence.service.webhook.WebhookService;
import org.nuxeo.runtime.api.Framework;

@Operation(
        id = TriggerWebhookOp.ID,
        category = "Hyland",
        label = "Trigger Hyland Webhook",
        description = "Calls Hx Automate webhook"
)
public class TriggerWebhookOp {

    public static final String ID = "HylandAutomateWebhookCall";

    @Context
    protected WebhookService service;

    @OperationMethod
    public void run(DocumentModel doc) {

        boolean ok = service.triggerWebhook(doc.getId());

        if (!ok) {
            throw new NuxeoException("Webhook failed");
        }
    }
}
