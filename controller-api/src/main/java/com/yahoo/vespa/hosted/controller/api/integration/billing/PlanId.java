// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.billing;

import java.util.Objects;

/**
 * @author olaa
 */
public class PlanId {

    private final String value;

    public PlanId(String value) {
        if (value.isBlank())
            throw new IllegalArgumentException("Id must be non-blank.");
        this.value = value;
    }

    public static PlanId from(String value) {
        return new PlanId(value);
    }

    public String value() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanId id = (PlanId) o;
        return Objects.equals(value, id.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "plan '" + value + "'";
    }

}
