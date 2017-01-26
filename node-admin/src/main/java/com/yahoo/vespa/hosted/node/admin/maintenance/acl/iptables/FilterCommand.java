package com.yahoo.vespa.hosted.node.admin.maintenance.acl.iptables;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author mpolden
 */
public class FilterCommand implements Command {

    private final Chain chain;
    private final Action action;
    private final List<Option> options;

    public FilterCommand(Chain chain, Action action) {
        this.chain = chain;
        this.action = action;
        this.options = new ArrayList<>();
    }

    public FilterCommand withOption(String name, String argument) {
        options.add(new Option(name, argument));
        return this;
    }

    @Override
    public String asString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("-A ").append(chain.name);
        if (!options.isEmpty()) {
            builder.append(" ")
                    .append(options.stream().map(Option::asString).collect(Collectors.joining(" ")));
        }
        builder.append(" -j ").append(action.name);
        return builder.toString();
    }

    private static class Option {
        private final String name;
        private final String argument;

        public Option(String name, String argument) {
            this.name = name;
            this.argument = argument;
        }

        public String asString() {
            return String.format("%s %s", name, argument);
        }
    }
}
