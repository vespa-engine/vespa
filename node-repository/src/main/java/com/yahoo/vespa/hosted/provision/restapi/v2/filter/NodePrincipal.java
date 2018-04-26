// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2.filter;

import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents the identity of a hosted Vespa node
 *
 * @author bjorncs
 */
public class NodePrincipal implements Principal {
    private final String identityName;
    private final String hostname;
    private final List<X509Certificate> clientCertificateChain;
    private final Type type;

    public static NodePrincipal withAthenzIdentity(String identityName,
                                                   List<X509Certificate> clientCertificateChain) {
        return withAthenzIdentity(identityName, null, clientCertificateChain);
    }

    public static NodePrincipal withAthenzIdentity(String identityName,
                                                   String hostname,
                                                   List<X509Certificate> clientCertificateChain) {
        return new NodePrincipal(identityName, hostname, clientCertificateChain, Type.ATHENZ);
    }

    public static NodePrincipal withLegacyIdentity(String hostname,
                                                   List<X509Certificate> clientCertificateChain) {
        return new NodePrincipal(hostname, hostname, clientCertificateChain, Type.LEGACY);
    }

    private NodePrincipal(String identityName,
                         String hostname,
                         List<X509Certificate> clientCertificateChain,
                         Type type) {
        this.identityName = identityName;
        this.hostname = hostname;
        this.clientCertificateChain = clientCertificateChain;
        this.type = type;
    }

    public String getHostIdentityName() {
        return identityName;
    }

    public Optional<String> getHostname() {
        return Optional.ofNullable(hostname);
    }

    public List<X509Certificate> getClientCertificateChain() {
        return clientCertificateChain;
    }

    public Type getType() {
        return type;
    }

    @Override
    public String getName() {
        return identityName;
    }

    public enum Type { ATHENZ, LEGACY }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodePrincipal principal = (NodePrincipal) o;
        return Objects.equals(identityName, principal.identityName) &&
                Objects.equals(hostname, principal.hostname) &&
                type == principal.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(identityName, hostname, type);
    }

    @Override
    public String toString() {
        return "NodePrincipal{" +
                "identityName='" + identityName + '\'' +
                ", hostname='" + hostname + '\'' +
                ", type=" + type +
                '}';
    }
}
