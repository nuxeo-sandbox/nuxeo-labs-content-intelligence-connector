package org.nuxeo.labs.hyland.content.intelligence.automation;

import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
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

    @Param(name = "input", required = false)
    protected String input = "hi";

    protected WebhookService service;

    @Context
    protected OperationContext ctx;

    @Context
    protected CoreSession session;

    @OperationMethod
    public void run() {

        service = Framework.getService(WebhookService.class);

        boolean ok = service.triggerWebhook(input);

        if (!ok) {
            throw new NuxeoException("Webhook failed");
        }
    }
}
