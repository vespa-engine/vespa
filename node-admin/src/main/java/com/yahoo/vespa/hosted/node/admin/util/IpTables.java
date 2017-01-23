package com.yahoo.vespa.hosted.node.admin.util;

/**
 * Utility class for creating iptables commands
 *
 * @author mpolden
 */
public class IpTables {

    private static final String COMMAND = "ip6tables";

    public static String[] allowFromAddress(String ipAddress) {
        return new String[]{COMMAND, "-A", "INPUT", "-s", ipAddress, "-j", "ACCEPT"};
    }

    public static String[] chainPolicy(Policy policy) {
        return new String[]{COMMAND, "-P", "INPUT", policy.target};
    }

    public static String[] flushChain() {
        return new String[]{COMMAND, "-F", "INPUT"};
    }

    public enum Policy {
        DROP("DROP"),
        ACCEPT("ACCEPT");

        private final String target;

        Policy(String target) {
            this.target = target;
        }
    }
}
