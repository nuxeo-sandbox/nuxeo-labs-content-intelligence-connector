package org.nuxeo.labs.hyland.content.intelligence.service.webhook;

import org.json.JSONObject;
import org.nuxeo.labs.hyland.content.intelligence.AuthenticationToken;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCall;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;

import java.util.HashMap;
import java.util.Map;

public class WebhookServiceImpl implements WebhookService {
    protected AuthenticationToken tokenManager;
    protected ServiceCall serviceCall = new ServiceCall();

    protected String webhookUrl;
    protected String tokenUrl;
    protected String clientId;
    protected String clientSecret;

    public void activate(ComponentContext context) {
        webhookUrl   = Framework.getProperty("nuxeo.hyland.webhook.url");
        tokenUrl     = Framework.getProperty("nuxeo.hyland.webhook.auth.url");
        clientId     = Framework.getProperty("nuxeo.hyland.webhook.clientId");
        clientSecret = Framework.getProperty("nuxeo.hyland.webhook.clientSecret");

        tokenManager = new AuthenticationToken(
                AuthenticationToken.ServiceType.WEBHOOK,
                tokenUrl,
                clientId,
                clientSecret
        );
    }

    @Override
    public boolean triggerWebhook(String input) {

        String token = tokenManager.getToken();
        if (token == null) return false;

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token);
        headers.put("Content-Type", "application/json");

        JSONObject body = new JSONObject();
        body.put("input", input);

        ServiceCallResult result =
                serviceCall.post(webhookUrl, headers, body.toString());

        return result.getResponseCode() == 201;
    }
}
