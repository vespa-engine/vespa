// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
