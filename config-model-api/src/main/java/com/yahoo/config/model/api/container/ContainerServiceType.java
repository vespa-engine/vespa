// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api.container;

/**
 * @author gjoranv
 */
public enum ContainerServiceType {

    CONTAINER("container"),
    QRSERVER("qrserver"),
    CLUSTERCONTROLLER_CONTAINER("container-clustercontroller"),
    LOGSERVER_CONTAINER("logserver-container"),
    METRICS_PROXY_CONTAINER("metrics-proxy-container");

    public final String name;

    ContainerServiceType(String name) {
        this.name = name;
    }

}
