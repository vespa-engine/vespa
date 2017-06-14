// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaclient;

public class ClusterDef {
    public ClusterDef(String name, String configId) {
        this.name = name;
        this.configId = configId;
    }

    String name;
    String configId;

    public String getName() { return name; }
    public String getConfigId() { return configId; }
}
