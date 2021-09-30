// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.applicationmodel;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;

/**
 * @author bjorncs
 */
public class ServiceType {

    // Common service types.
    public static final ServiceType CONTAINER = new ServiceType("container");
    public static final ServiceType SLOBROK = new ServiceType("slobrok");
    public static final ServiceType HOST_ADMIN = new ServiceType("hostadmin");
    public static final ServiceType CONFIG_SERVER = new ServiceType("configserver");
    public static final ServiceType CONTROLLER = new ServiceType("controller");
    public static final ServiceType TRANSACTION_LOG_SERVER = new ServiceType("transactionlogserver");
    public static final ServiceType CLUSTER_CONTROLLER = new ServiceType("container-clustercontroller");
    public static final ServiceType DISTRIBUTOR = new ServiceType("distributor");
    public static final ServiceType SEARCH = new ServiceType("searchnode");
    public static final ServiceType STORAGE = new ServiceType("storagenode");
    public static final ServiceType METRICS_PROXY = new ServiceType("metricsproxy-container");

    private final String id;

    public ServiceType(String id) {
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
        ServiceType that = (ServiceType) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

}
