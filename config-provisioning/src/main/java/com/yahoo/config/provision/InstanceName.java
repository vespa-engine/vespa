// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.Objects;

/**
 * Represents an applications instance name, which may be any kind of string or default. This type is defined
 * in order to provide a type safe API for defining environments.
 *
 * @author Ulf Lilleengen
 */
public class InstanceName implements Comparable<InstanceName> {

    private static final InstanceName defaultInstance = new InstanceName("default");

    private final String instanceName;

    private InstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    @Override
    public int hashCode() {
        return instanceName.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof InstanceName)) return false;
        return Objects.equals(((InstanceName) obj).instanceName, instanceName);
    }

    @Override
    public String toString() {
        return instanceName;
    }

    public static InstanceName from(String name) {
        return new InstanceName(name);
    }

    public static InstanceName defaultName() {
        return defaultInstance;
    }

    public boolean isDefault() {
        return equals(InstanceName.defaultName());
    }

    public boolean isTester() {
        return value().endsWith("-t");
    }

    public String value() { return instanceName; }

    @Override
    public int compareTo(InstanceName instance) {
        return instanceName.compareTo(instance.instanceName);
    }

}
