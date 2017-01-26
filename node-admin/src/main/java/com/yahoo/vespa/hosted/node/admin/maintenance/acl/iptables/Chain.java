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
