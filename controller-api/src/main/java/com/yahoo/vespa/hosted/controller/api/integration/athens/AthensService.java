// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athens;

import com.yahoo.vespa.hosted.controller.api.identifiers.AthensDomain;

import java.util.Objects;

/**
 * @author bjorncs
 */
public class AthensService {

    private final AthensDomain domain;
    private final String serviceName;

    public AthensService(AthensDomain domain, String serviceName) {
        this.domain = domain;
        this.serviceName = serviceName;
    }

    public String toFullServiceName() {
        return domain.id() + "." + serviceName;
    }

    public AthensDomain getDomain() {
        return domain;
    }

    public String getServiceName() {
        return serviceName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AthensService that = (AthensService) o;
        return Objects.equals(domain, that.domain) &&
                Objects.equals(serviceName, that.serviceName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(domain, serviceName);
    }

    @Override
    public String toString() {
        return String.format("AthensService(%s)", toFullServiceName());
    }
}
