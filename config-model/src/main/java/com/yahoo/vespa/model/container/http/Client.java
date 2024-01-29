// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.config.provision.DataplaneToken;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.model.container.http.Client.Permission.READ;
import static com.yahoo.vespa.model.container.http.Client.Permission.WRITE;

/**
 * Represents a client. The client is identified by one of the provided certificates and have a set of permissions.
 *
 * @author mortent
 * @author bjorncs
 */
public class Client {
    private final String id;
    private final Set<Permission> permissions;
    private final List<X509Certificate> certificates;
    private final List<DataplaneToken> tokens;
    private final boolean internal;

    public Client(String id, Collection<Permission> permissions, List<X509Certificate> certificates, List<DataplaneToken> tokens) {
        this(id, permissions, certificates, tokens, false);
    }

    private Client(String id, Collection<Permission> permissions, List<X509Certificate> certificates, List<DataplaneToken> tokens,
                   boolean internal) {
        this.id = id;
        this.permissions = Set.copyOf(permissions);
        this.certificates = List.copyOf(certificates);
        this.tokens = List.copyOf(tokens);
        this.internal = internal;
    }

    public String id() {
        return id;
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    public List<X509Certificate> certificates() {
        return certificates;
    }

    public List<DataplaneToken> tokens() { return tokens; }

    public boolean internal() {
        return internal;
    }

    public static Client internalClient(List<X509Certificate> certificates) {
        return new Client("_internal", Set.of(READ, WRITE), certificates, List.of(), true);
    }

    public enum Permission {
        READ, WRITE;

        public String asString() {
            return switch (this) {
                case READ -> "read";
                case WRITE -> "write";
            };
        }

        public static Permission fromString(String v) {
            return switch (v) {
                case "read" -> READ;
                case "write" -> WRITE;
                default -> throw new IllegalArgumentException("Invalid permission '%s'. Valid values are 'read' and 'write'.".formatted(v));
            };
        }

        public static Set<Permission> fromCommaSeparatedString(String str) {
            return Stream.of(str.split(",")).map(v -> Permission.fromString(v.strip())).collect(Collectors.toSet());
        }
    }
}
