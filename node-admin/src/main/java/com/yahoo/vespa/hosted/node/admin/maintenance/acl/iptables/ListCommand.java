package com.yahoo.vespa.hosted.node.admin.maintenance.acl.iptables;

/**
 * @author mpolden
 */
public class ListCommand implements Command {
    @Override
    public String asString() {
        return "-S";
    }
}
