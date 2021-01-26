// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import java.util.Objects;

/**
 * Credentials for a container registry server.
 *
 * @author mpolden
 */
// TODO: Move this to node-admin when docker-api module can be removed
public class RegistryCredentials {

    public static final RegistryCredentials none = new RegistryCredentials("", "", "");

    private final String username;
    private final String password;
    private final String registryAddress;

    public RegistryCredentials(String username, String password, String registryAddress) {
        this.username = Objects.requireNonNull(username);
        this.password = Objects.requireNonNull(password);
        this.registryAddress = Objects.requireNonNull(registryAddress);
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public String registryAddress() {
        return registryAddress;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegistryCredentials that = (RegistryCredentials) o;
        return username.equals(that.username) &&
               password.equals(that.password) &&
               registryAddress.equals(that.registryAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, password, registryAddress);
    }

    @Override
    public String toString() {
        return "registry credentials for " + registryAddress + " [username=" + username + ",password=" + password + "]";
    }

}
