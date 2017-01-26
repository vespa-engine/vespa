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
