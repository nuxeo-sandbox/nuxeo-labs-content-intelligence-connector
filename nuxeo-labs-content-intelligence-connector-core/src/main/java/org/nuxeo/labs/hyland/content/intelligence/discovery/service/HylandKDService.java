package org.nuxeo.labs.hyland.content.intelligence.discovery.service;

import org.nuxeo.labs.hyland.content.intelligence.http.ServiceCallResult;

public interface HylandKDService {
    
    public ServiceCallResult invokeDiscovery(String httpMethod, String endpoint, String jsonPayload);
}
