package com.yahoo.vespa.hosted.node.admin.util;

/**
 * Utility class for creating iptables commands
 *
 * @author mpolden
 */
public class IpTables {

    private static final String COMMAND = "ip6tables";

    public static String[] allowFromAddress(String ipAddress) {
        return new String[]{COMMAND, "-A", Chain.INPUT.name, "-s", ipAddress, "-j", Action.ACCEPT.target};
    }

    public static String[] allowAssociatedConnections() {
        return new String[]{COMMAND, "-A", Chain.INPUT.name, "-m", "state", "--state", "RELATED,ESTABLISHED",  "-j",
                Action.ACCEPT.target};
    }

    public static String[] chainPolicy(Action action) {
        return new String[]{COMMAND, "-P", Chain.INPUT.name, action.target};
    }

    public static String[] flushChain() {
        return new String[]{COMMAND, "-F", Chain.INPUT.name};
    }

    public enum Action {
        DROP("DROP"),
        ACCEPT("ACCEPT");

        private final String target;

        Action(String target) {
            this.target = target;
        }
    }

    public enum Chain {
        INPUT("INPUT");

        private final String name;

        Chain(String name) {
            this.name = name;
        }
    }
}
