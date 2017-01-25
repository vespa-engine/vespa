package com.yahoo.vespa.hosted.node.admin.maintenance.acl.iptables;

/**
 * @author mpolden
 */
public class FlushCommand implements Command {

    private final Chain chain;

    public FlushCommand(Chain chain) {
        this.chain = chain;
    }

    @Override
    public String asString() {
        return String.format("-F %s", chain.name);
    }
}
