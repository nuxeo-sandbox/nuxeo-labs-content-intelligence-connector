package org.nuxeo.labs.hyland.content.intelligence.service.webhook;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.nuxeo.labs.hyland.content.intelligence.AuthenticationToken;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCall;
import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;

import java.util.HashMap;
import java.util.Map;

public class WebhookServiceImpl implements WebhookService {

    private static final Logger log = LogManager.getLogger(WebhookServiceImpl.class);
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
    public boolean triggerWebhook(String docId) {

        String token = tokenManager.getToken();
        if (token == null) {
            log.error("Webhook trigger failed: token is null");
            return false;
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token);
        headers.put("Content-Type", "application/json");

        JSONObject body = new JSONObject();
        body.put("docId", docId);

        ServiceCallResult result =
                serviceCall.post(webhookUrl, headers, body.toString());

        int code = result.getResponseCode();
        log.info("Webhook response code: {}", code);

        return code >= 200 && code < 300;

        //return result.getResponseCode() == 201;
    }
}
