// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
