// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Represents a client. The client is identified by one of the provided certificates and have a set of permissions.
 *
 * @author mortent
 */
public class Client {
    private String id;
    private List<String> permissions;
    private List<X509Certificate> certificates;

    public Client(String id, List<String> permissions, List<X509Certificate> certificates) {
        this.id = id;
        this.permissions = permissions;
        this.certificates = certificates;
    }

//    public static Client createLegacyClient(List<X509Certificate> certificates) {
//        return new Client(true, "default", List.of(AclMapping.Action.READ.name(), AclMapping.Action.WRITE.name()), certificates);
//    }
//
//    public static Client createClient(String id, List<String> permissions, List<X509Certificate> certificates) {
//        return new Client(false, id, permissions, certificates);
//    }
//

    public String id() {
        return id;
    }

    public List<String> permissions() {
        return permissions;
    }

    public List<X509Certificate> certificates() {
        return certificates;
    }
}
