package org.nuxeo.labs.hyland.content.intelligence.service.webhook;

public interface WebhookService {
    boolean triggerWebhook(String inputPayload);
}
