package com.yahoo.vespa.hosted.controller.application;

import java.util.Objects;

public class EndpointId {
    private static final EndpointId DEFAULT = new EndpointId("default");

    private final String id;

    public EndpointId(String id) {
        this.id = requireNotEmpty(id);
    }

    public String id() { return id; }

    @Override
    public String toString() {
        return "EndpointId{" +
                "id='" + id + '\'' +
                '}';
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

    public static EndpointId default_() { return DEFAULT; }

    public static EndpointId of(String id) { return new EndpointId(id); }
}
