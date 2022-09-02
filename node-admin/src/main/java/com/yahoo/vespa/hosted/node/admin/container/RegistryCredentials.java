// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container;

import java.util.Objects;

/**
 * Credentials for a container registry server.
 *
 * @author mpolden
 */
public record RegistryCredentials(String username, String password) {

    public static final RegistryCredentials none = new RegistryCredentials("", "");

    public RegistryCredentials {
        Objects.requireNonNull(username);
        Objects.requireNonNull(password);
    }

    @Override
    public String toString() {
        return "registry credentials [username=" + username + ",password=<hidden>]";
    }

}
