// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.applicationmodel;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;

/**
 * @author bjorncs
 */
public class ClusterId {

    public static final ClusterId CONFIG_SERVER = new ClusterId(InfrastructureApplication.CONFIG_SERVER.applicationName());
    public static final ClusterId CONTROLLER = new ClusterId(InfrastructureApplication.CONTROLLER.applicationName());
    public static final ClusterId CONFIG_SERVER_HOST = new ClusterId(InfrastructureApplication.CONFIG_SERVER_HOST.applicationName());
    public static final ClusterId CONTROLLER_HOST = new ClusterId(InfrastructureApplication.CONTROLLER_HOST.applicationName());
    public static final ClusterId PROXY_HOST = new ClusterId(InfrastructureApplication.PROXY_HOST.applicationName());
    public static final ClusterId ROUTING = new ClusterId(InfrastructureApplication.PROXY.applicationName());
    public static final ClusterId TENANT_HOST = new ClusterId(InfrastructureApplication.TENANT_HOST.applicationName());

    private final String id;

    public ClusterId(String id) {
        this.id = id;
    }

    // Jackson's StdKeySerializer uses toString() (and ignores annotations) for objects used as Map keys.
    // Therefore, we use toString() as the JSON-producing method, which is really sad.
    @JsonValue
    @Override
    public String toString() {
        return id;
    }

    // For compatibility with original Scala case class
    public String s() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClusterId clusterId = (ClusterId) o;
        return Objects.equals(id, clusterId.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

}
