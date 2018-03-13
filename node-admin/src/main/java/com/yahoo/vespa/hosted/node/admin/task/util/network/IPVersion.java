package com.yahoo.vespa.hosted.node.admin.task.util.network;

/**
 * Strong type IPv4 and IPv6 with the respective iptables executable.
 *
 * @author smorgrav
 */
public enum IPVersion {

    IPv6("ip6tables"),
    IPv4("iptables");

    IPVersion(String exec) {
        this.exec = exec;
    }

    private String exec;

    String exec() {
        return exec;
    }
}
