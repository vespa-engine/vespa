package com.yahoo.vespa.hosted.node.admin;

/**
 * An ACL specification for a container.
 *
 * @author mpolden
 */
public class ContainerAclSpec {

    private final String hostname;
    private final String ipAddress;
    private final String trustedBy;

    public ContainerAclSpec(String hostname, String ipAddress, String trustedBy) {
        this.hostname = hostname;
        this.ipAddress = ipAddress;
        this.trustedBy = trustedBy;
    }

    public String hostname() {
        return hostname;
    }

    public String ipAddress() {
        return ipAddress;
    }

    public String trustedBy() {
        return trustedBy;
    }
}
