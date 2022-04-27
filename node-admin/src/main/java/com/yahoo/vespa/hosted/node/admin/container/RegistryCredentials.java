// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container;

import java.util.Objects;

/**
 * Credentials for a container registry server.
 *
 * @author mpolden
 */
public class RegistryCredentials {

    public static final RegistryCredentials none = new RegistryCredentials("", "");

    private final String username;
    private final String password;

    public RegistryCredentials(String username, String password) {
        this.username = Objects.requireNonNull(username);
        this.password = Objects.requireNonNull(password);
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegistryCredentials that = (RegistryCredentials) o;
        return username.equals(that.username) &&
               password.equals(that.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, password);
    }

    @Override
    public String toString() {
        return "registry credentials [username=" + username + ",password=<hidden>]";
    }

}
