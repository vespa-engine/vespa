// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.builder.xml;

import com.yahoo.component.Version;

/**
 * A {@link ConfigModelId} describes an element handled by a {@link ConfigModelBuilder}.
 *
 * @author Ulf Lilleengen
 */
public class ConfigModelId implements Comparable<ConfigModelId> {

    private final String name;
    private final Version version;
    private final String stringValue;

    private ConfigModelId(String name, Version version) {
        this.name = name;
        this.version = version;
        this.stringValue = toStringValue();
    }

    /**
     * Create id with a name and version
     * @param tagName Name of the id
     * @param tagVersion Version of the id
     * @return A ConfigModelId instance
     */
    public static ConfigModelId fromNameAndVersion(String tagName, String tagVersion) {
        return new ConfigModelId(tagName, Version.fromString(tagVersion));
    }

    /**
     * Create id with given name, using default version 1.
     *
     * @param tagName Name of the id
     * @return A ConfigModelId instance
     */
    public static ConfigModelId fromName(String tagName) {
        return new ConfigModelId(tagName, new Version(1));
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof ConfigModelId other)) return false;
        return this.name.equals(other.name) && this.version.equals(other.version);
    }

    @Override
    public int compareTo(ConfigModelId other) {
        if (other == this) return 0;
        int cmp = this.name.compareTo(other.name);
        if (cmp == 0) {
            cmp = this.version.compareTo(other.version);
        }
        return cmp;
    }

    @Override
    public String toString() {
        return stringValue;
    }

    @Override
    public int hashCode() {
        return stringValue.hashCode();
    }

    /**
     * Return the XML element name.
     * @return the name of the config model
     */
    public String getName() {
        return name;
    }

    /**
     * Return the XML element version.
     * @return the version of the config model
     */
    Version getVersion() {
        return version;
    }

    private String toStringValue() {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        sb.append(".");
        sb.append(version);
        return sb.toString();
    }
}
