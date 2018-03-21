package com.yahoo.vespa.hosted.node.admin.task.util.network;

/**
 * Strong type IPv4 and IPv6 with common executables for ip related commands.
 *
 * @author smorgrav
 */
public enum IPVersion {

    IPv6("ip6tables", "ip -6"),
    IPv4("iptables", "ip");

    IPVersion(String iptablesCmd, String ipCmd) {
        this.ipCmd = ipCmd;
        this.iptablesCmd = iptablesCmd;
    }

    private String iptablesCmd;
    private String ipCmd;

    public String iptablesCmd() {
        return iptablesCmd;
    }
    public String ipCmd() {
        return ipCmd;
    }
}
