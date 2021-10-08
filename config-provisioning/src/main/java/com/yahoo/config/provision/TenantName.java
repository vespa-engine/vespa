// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.Objects;

/**
 * Represents a tenant in the provision API.
 *
 * @author Ulf Lilleengen
 */
public class TenantName implements Comparable<TenantName> {

    private final String name;

    private TenantName(String name) {
        this.name = name;
    }

    public String value() { return name; }

    /**
     * Create a {@link TenantName} with a given name.
     *
     * @param name Name of tenant.
     * @return instance of {@link TenantName}.
     */
    public static TenantName from(String name) {
        return new TenantName(name);
    }
    
    @Override
    public int hashCode() {
    	return name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
    	if (!(obj instanceof TenantName)) return false;
    	return Objects.equals(((TenantName)obj).value(), value());
    }

    @Override
    public String toString() {
        return name;
    }

    public static TenantName defaultName() {
        return from("default");
    }

    @Override
    public int compareTo(TenantName that) {
        return this.name.compareTo(that.name);
    }

}
