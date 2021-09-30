// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.Objects;

/**
 * A host name
 *
 * @author mortent
 */
public class HostName implements Comparable<HostName> {

    private final String name;

    private HostName(String name) {
        this.name = name;
    }

    public String value() { return name; }

    /** Create a {@link HostName} with a given name */
    public static HostName from(String name) {
        return new HostName(name);
    }
    
    @Override
    public int hashCode() {
    	return name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
    	if (!(obj instanceof HostName)) return false;
    	return Objects.equals(((HostName)obj).value(), value());
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int compareTo(HostName that) {
        return this.name.compareTo(that.name);
    }

}
