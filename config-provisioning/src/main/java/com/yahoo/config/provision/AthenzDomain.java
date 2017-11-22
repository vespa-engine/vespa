// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 * @author mortent
 */
public class AthenzDomain {

    private final String name;

    private AthenzDomain(String name) {
        this.name = name;
    }

    public static AthenzDomain from(String value) {
        return new AthenzDomain(value);
    }

    public String value() { return name; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AthenzDomain that = (AthenzDomain) o;

        return name != null ? name.equals(that.name) : that.name == null;
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }
}
