// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.acl.iptables;

/**
 * Represents a single iptables command
 *
 * @author mpolden
 */
public interface Command {

    String asString();

    default String asString(String commandName) {
        return commandName + " "  + asString();
    }

    default String[] asArray(String commandName) {
        return asString(commandName).split(" ");
    }
}
