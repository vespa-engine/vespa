// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaclient;

public class ClusterDef {
    private final String name;
    public ClusterDef(String name) { this.name = name; }
    public String getName() { return name; }
    public String getRoute() { return "[Content:cluster=" + name + "]"; }
}
