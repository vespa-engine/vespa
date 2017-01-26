package com.yahoo.vespa.hosted.node.admin.maintenance.acl.iptables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a list of iptables commands
 *
 * @author mpolden
 */
public class CommandList {

    private final List<Command> commands;

    public CommandList() {
        this.commands = new ArrayList<>();
    }

    public CommandList add(Command command) {
        this.commands.add(command);
        return this;
    }

    public CommandList addAll(List<Command> commands) {
        this.commands.addAll(commands);
        return this;
    }

    public List<Command> commands() {
        return Collections.unmodifiableList(this.commands);
    }

    public String asString() {
        return commands.stream()
                .map(Command::asString)
                .collect(Collectors.joining("\n"));
    }
}
