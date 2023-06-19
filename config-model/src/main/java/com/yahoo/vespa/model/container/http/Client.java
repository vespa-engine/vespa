// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.config.provision.DataplaneToken;

import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Represents a client. The client is identified by one of the provided certificates and have a set of permissions.
 *
 * @author mortent
 */
public class Client {
    private final String id;
    private final List<String> permissions;
    private final List<X509Certificate> certificates;
    private final List<DataplaneToken> tokens;
    private final boolean internal;

    public Client(String id, List<String> permissions, List<X509Certificate> certificates, List<DataplaneToken> tokens) {
        this(id, permissions, certificates, tokens, false);
    }

    private Client(String id, List<String> permissions, List<X509Certificate> certificates, List<DataplaneToken> tokens,
                   boolean internal) {
        this.id = id;
        this.permissions = List.copyOf(permissions);
        this.certificates = List.copyOf(certificates);
        this.tokens = List.copyOf(tokens);
        this.internal = internal;
    }

    public String id() {
        return id;
    }

    public List<String> permissions() {
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
        return new Client("_internal", List.of("read","write"), certificates, List.of(), true);
    }
}
