// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api;

import com.yahoo.config.provision.ApplicationId;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * A DNS alias for an application endpoint.
 *
 * @author smorgrav
 */
public class ApplicationAlias {

    private static final String dnsSuffix = "global.vespa.yahooapis.com";

    private final ApplicationId applicationId;

    public ApplicationAlias(ApplicationId applicationId) {
        this.applicationId = applicationId;
    }

    @Override
    public String toString() {
        return String.format("%s.%s.%s", 
                             toDns(applicationId.application().value()), 
                             toDns(applicationId.tenant().value()), 
                             dnsSuffix);
    }

    private String toDns(String id) {
        return id.replace('_', '-');
    }

    public URI toHttpUri() {
        try {
            return new URI("http://" + this + ":4080/");
        } catch(URISyntaxException use) {
            throw new RuntimeException("Illegal URI syntax");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ApplicationAlias that = (ApplicationAlias) o;

        return applicationId.equals(that.applicationId);
    }

    @Override
    public int hashCode() { return applicationId.hashCode(); }

}
