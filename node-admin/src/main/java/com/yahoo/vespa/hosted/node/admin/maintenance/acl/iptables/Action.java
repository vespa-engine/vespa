package com.yahoo.vespa.hosted.node.admin.maintenance.acl.iptables;

/**
 * @author mpolden
 */
public enum Action {
    DROP("DROP"),
    REJECT("REJECT"),
    ACCEPT("ACCEPT");

    public final String name;

    Action(String name) {
        this.name = name;
    }
}
