package com.yahoo.vespa.model.container;

/**
 * @author mortent
 */
public class Identity {
    private final String domain;
    private final String service;

    public Identity(String domain, String service) {
        this.domain = domain;
        this.service = service;
    }

    public String getDomain() {
        return domain;
    }

    public String getService() {
        return service;
    }
}
