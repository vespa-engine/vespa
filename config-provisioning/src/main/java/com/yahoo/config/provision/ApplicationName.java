// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import ai.vespa.validation.PatternedStringWrapper;

/**
 * Represents an applications name, which may be any kind of string or default. This type is defined
 * in order to provide a type safe API for defining environments.
 *
 * @author Ulf Lilleengen
 * @since 5.25
 */
public class ApplicationName extends PatternedStringWrapper<ApplicationName> {

    private static final ApplicationName defaultName = new ApplicationName("default");

    private ApplicationName(String name) {
        super(name, ApplicationId.namePattern, "application name");
    }

    public static ApplicationName from(String name) {
        return new ApplicationName(name);
    }

    public static ApplicationName defaultName() {
        return defaultName;
    }

    public boolean isDefault() {
        return equals(defaultName);
    }

}
