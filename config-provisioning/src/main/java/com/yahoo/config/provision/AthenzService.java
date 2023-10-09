// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 * @author mortent
 */
public class AthenzService {

    private final String name;

    private AthenzService(String name) {
        this.name = name;
    }

    public String value() { return name; }

    public static AthenzService from(String value) {
        return new AthenzService(value);
    }

    @Override
    public String toString() {
        return "AthenzService{" +
               "name='" + name + '\'' +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AthenzService that = (AthenzService) o;

        return name != null ? name.equals(that.name) : that.name == null;
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }
}
