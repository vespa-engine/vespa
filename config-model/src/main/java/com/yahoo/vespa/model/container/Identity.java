// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
