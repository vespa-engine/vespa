// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.Objects;

/**
 * Represents an applications name, which may be any kind of string or default. This type is defined
 * in order to provide a type safe API for defining environments.
 *
 * @author Ulf Lilleengen
 * @since 5.25
 */
public class ApplicationName implements Comparable<ApplicationName> {

    private final String applicationName;

    private ApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    @Override
    public int hashCode() {
        return applicationName.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ApplicationName)) return false;
        return Objects.equals(((ApplicationName) obj).applicationName, applicationName);
    }

    @Override
    public String toString() {
        return applicationName;
    }

    public static ApplicationName from(String name) {
        return new ApplicationName(name);
    }

    public static ApplicationName defaultName() {
        return new ApplicationName("default");
    }

    public boolean isDefault() {
        return equals(ApplicationName.defaultName());
    }

    public String value() {
        return applicationName;
    }

    @Override
    public int compareTo(ApplicationName name) {
        return this.applicationName.compareTo(name.applicationName);
    }

}
