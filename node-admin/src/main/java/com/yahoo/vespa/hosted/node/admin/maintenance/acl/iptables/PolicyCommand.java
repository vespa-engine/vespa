package com.yahoo.vespa.hosted.node.admin.maintenance.acl.iptables;

/**
 * @author mpolden
 */
public class PolicyCommand implements Command {

    private final Chain chain;
    private final Action policy;

    public PolicyCommand(Chain chain, Action policy) {
        this.chain = chain;
        this.policy = policy;
    }

    @Override
    public String asString() {
        return String.format("-P %s %s", chain.name, policy.name);
    }
}
