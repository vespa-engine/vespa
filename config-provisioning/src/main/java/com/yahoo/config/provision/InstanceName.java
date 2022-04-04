// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import ai.vespa.validation.PatternedStringWrapper;

/**
 * Represents an applications instance name, which may be any kind of string or default. This type is defined
 * in order to provide a type safe API for defining environments.
 *
 * @author Ulf Lilleengen
 */
public class InstanceName extends PatternedStringWrapper<InstanceName> {

    private static final InstanceName defaultName = new InstanceName("default");

    private InstanceName(String name) {
        super(name, ApplicationId.namePattern, "instance name");
    }

    public static InstanceName from(String name) {
        return new InstanceName(name);
    }

    public static InstanceName defaultName() {
        return defaultName;
    }

    public boolean isDefault() {
        return equals(defaultName);
    }

    public boolean isTester() {
        return value().endsWith("-t");
    }

}
