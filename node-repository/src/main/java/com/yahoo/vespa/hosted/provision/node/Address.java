// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import java.util.Objects;

/**
 * Address info about a container that might run on a host.
 *
 * @author hakon
 */
public class Address {
    private final String hostname;

    public Address(String hostname) {
        this.hostname = validateHostname(hostname, "hostname");
    }

    public String hostname() {
        return hostname;
    }

    @Override
    public String toString() {
        return "Address{" +
                "hostname='" + hostname + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Address address = (Address) o;
        return hostname.equals(address.hostname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname);
    }

    private String validateHostname(String value, String name) {
        Objects.requireNonNull(value, name + " cannot be null");
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be empty");
        }
        return value;
    }
}
