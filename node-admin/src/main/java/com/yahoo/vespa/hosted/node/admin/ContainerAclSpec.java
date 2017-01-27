package com.yahoo.vespa.hosted.node.admin;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContainerAclSpec that = (ContainerAclSpec) o;
        return Objects.equals(hostname, that.hostname) &&
                Objects.equals(ipAddress, that.ipAddress) &&
                Objects.equals(trustedBy, that.trustedBy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname, ipAddress, trustedBy);
    }
}
