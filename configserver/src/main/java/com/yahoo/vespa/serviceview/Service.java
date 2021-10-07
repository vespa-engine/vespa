// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.serviceview;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.yahoo.text.Utf8;

/**
 * Model a single service instance as a sortable object.
 *
 * @author Steinar Knutsen
 */
public final class Service implements Comparable<Service> {

    public final String serviceType;
    public final String host;
    public final int statePort;
    public final String configId;
    public final List<Integer> ports;
    public final String name;

    public Service(String serviceType, String host, int statePort, String clusterName, String clusterType,
            String configId, List<Integer> ports, String name) {
        this.serviceType = serviceType;
        this.host = host.toLowerCase();
        this.statePort = statePort;
        this.configId = configId;
        ImmutableList.Builder<Integer> portsBuilder = new ImmutableList.Builder<>();
        portsBuilder.addAll(ports);
        this.ports = portsBuilder.build();
        this.name = name;
    }

    @Override
    public int compareTo(Service other) {
        int serviceTypeOrder = serviceType.compareTo(other.serviceType);
        if (serviceTypeOrder != 0) {
            return serviceTypeOrder;
        }
        int hostOrder = host.compareTo(other.host);
        if (hostOrder != 0) {
            return hostOrder;
        }
        return Integer.compare(statePort, other.statePort);
    }

    /**
     * Generate an identifier string for one of the ports of this service
     * suitable for using in an URL.
     *
     * @param port
     *            port which this identifier pertains to
     * @return an opaque identifier string for this service
     */
    public String getIdentifier(int port) {
        StringBuilder b = new StringBuilder(serviceType);
        b.append("-");
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 should by definition always be available in the JVM.", e);
        }
        md5.update(Utf8.toBytes(serviceType));
        md5.update(Utf8.toBytes(configId));
        md5.update(Utf8.toBytes(host));
        for (int i = 3; i >= 0; --i) {
            md5.update((byte) (port >>> i));
        }
        byte[] digest = md5.digest();
        BigInteger digestMarshal = new BigInteger(1, digest);
        b.append(digestMarshal.toString(36));
        return b.toString();
    }

    /**
     * All valid identifiers for this object.
     *
     * @return a list with a unique ID for each of this service's ports
     */
    public List<String> getIdentifiers() {
        List<String> ids = new ArrayList<>(ports.size());
        for (int port : ports) {
            ids.add(getIdentifier(port));
        }
        return ids;
    }

    /**
     * Find which port number a hash code pertains to.
     *
     * @param identifier a string generated from {@link #getIdentifier(int)}
     * @return a port number, or 0 if no match is found
     */
    public int matchIdentifierWithPort(String identifier) {
        for (int port : ports) {
            if (identifier.equals(getIdentifier(port))) {
                return port;
            }
        }
        throw new IllegalArgumentException("Identifier " + identifier + " matches no ports in " + this);
    }

    @Override
    public String toString() {
        final int maxLen = 3;
        StringBuilder builder = new StringBuilder();
        builder.append("Service [serviceType=").append(serviceType).append(", host=").append(host).append(", statePort=")
                .append(statePort).append(", configId=").append(configId).append(", ports=")
                .append(ports.subList(0, Math.min(ports.size(), maxLen))).append(", name=").append(name)
                .append("]");
        return builder.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 131;
        int result = 1;
        result = prime * result + configId.hashCode();
        result = prime * result + host.hashCode();
        result = prime * result + name.hashCode();
        result = prime * result + ports.hashCode();
        result = prime * result + serviceType.hashCode();
        result = prime * result + statePort;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Service other = (Service) obj;
        if (!configId.equals(other.configId)) {
            return false;
        }
        if (!host.equals(other.host)) {
            return false;
        }
        if (!name.equals(other.name)) {
            return false;
        }
        if (!ports.equals(other.ports)) {
            return false;
        }
        if (!serviceType.equals(other.serviceType)) {
            return false;
        }
        return statePort == other.statePort;
    }

}
