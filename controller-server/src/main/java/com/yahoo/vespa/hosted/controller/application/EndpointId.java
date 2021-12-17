// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import java.util.Objects;

/**
 * A type to represent the ID of an endpoint.  This is typically the first part of
 * an endpoint name.
 *
 * @author ogronnesby
 */
public class EndpointId implements Comparable<EndpointId> {

    private static final EndpointId DEFAULT = new EndpointId("default");

    private final String id;

    private EndpointId(String id) {
        this.id = requireNotEmpty(id);
    }

    public String id() { return id; }

    @Override
    public String toString() {
        return "endpoint id '" + id + "'";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EndpointId that = (EndpointId) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    private static String requireNotEmpty(String input) {
        Objects.requireNonNull(input);
        if (input.isEmpty()) {
            throw new IllegalArgumentException("The value EndpointId was empty");
        }
        return input;
    }

    public static EndpointId defaultId() { return DEFAULT; }

    public static EndpointId of(String id) { return new EndpointId(id); }

    @Override
    public int compareTo(EndpointId o) {
        return id.compareTo(o.id);
    }

}
