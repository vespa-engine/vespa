// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.acl.iptables;

/**
 * @author mpolden
 */
public enum Chain {
    INPUT("INPUT"),
    FORWARD("FORWARD"),
    OUTPUT("OUTPUT");

    public final String name;

    Chain(String name) {
        this.name = name;
    }
}
