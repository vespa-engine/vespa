// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 * The {@link Version} class is used in providing versioned config for applications.
 *
 * A {@link Version} object has three components:
 *
 * * Major version. A non-negative integer.
 * * Minor version. A non-negative integer.
 * * Micro version. A non-negative integer.
 *
 * @author Vegard Sjonfjell
 * @since 5.39
 * Loosely based on component/Version.java
 * {@link Version} objects are immutable.
 */
// TODO: Replace usage of this by com.yahoo.component.Version
public final class Version implements Comparable<Version> {

    private final int major;
    private final int minor;
    private final int micro;
    private final String stringValue;

    /**
     * @see #fromIntValues
     */
    private Version(int major, int minor, int micro) {
        this.major = major;
        this.minor = minor;
        this.micro = micro;
        stringValue = toSerializedForm();
        verify();
    }

    /**
     * @see #fromString
     */
    private Version(String versionString) {
        try {
            String[] components = versionString.split("\\.", 3);
            assert (components.length == 3);
            major = Integer.parseInt(components[0]);
            minor = Integer.parseInt(components[1]);
            micro = Integer.parseInt(components[2]);
            stringValue = toSerializedForm();
            verify();
        } catch (AssertionError | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format("Invalid version specification: \"%s\": %s", versionString, e.getMessage()));
        }
    }

    /**
     * Verifies that the numerical components in a version are legal.
     * Must be called on construction after the component values are set
     *
     * @throws IllegalArgumentException if one of the numerical components are negative.
     */
    private void verify() {
        if (major < 0)
            throw new IllegalArgumentException("Negative major value");
        if (minor < 0)
            throw new IllegalArgumentException("Negative minor value");
        if (micro < 0)
            throw new IllegalArgumentException("Negative micro value");
    }

    public String toSerializedForm() {
        return String.format("%d.%d.%d", major, minor, micro);
    }

    /**
     * Creates a {@link Version} object from the specified components.
     *
     * @param major major component of the version identifier.
     * @param minor minor component of the version identifier.
     * @param micro micro component of the version identifier.
     * @throws IllegalArgumentException if one of the numerical components are negative.
     * @return {@link Version} identifier object constructed from integer components.
     */
    public static Version fromIntValues(int major, int minor, int micro) {
        return new Version(major, minor, micro);
    }

    /**
     * Creates a version object from the specified string. Version strings are in the format major.minor.micro
     *
     * @param versionString String representation of the version.
     * @throws IllegalArgumentException If version string is improperly formatted.
     * @return {@link Version} object constructed from string representation.
     */
    public static Version fromString(String versionString) {
        return new Version(versionString);
    }

    /**
     * Returns a string representation of this version identifier, encoded as major.minor.micro
     */
    public String toString() { return stringValue; }

    /**
     * Returns major version component
     */
    public int getMajor() { return major; }

    /**
     * Returns minor version component
     */
    public int getMinor() { return minor; }

    /**
     * Returns micro version component
     */
    public int getMicro() { return micro; }

    @Override
    public int hashCode() { return stringValue.hashCode(); }

    /**
     * Performs an equality test between this {@link Version} object and another.
     *
     * A version is considered to be equal to another version if the
     * major, minor and micro components are equal.
     *
     * @param object The {@link Version} object to be compared to this version.
     * @return <code>true</code> if object is a
     *         {@link Version} and is equal to this object;
     *         <code>false</code> otherwise.
     */
    @Override
    public boolean equals(Object object) {
        if (object == null || object.getClass() != this.getClass()) {
            return false;
        }

        Version other = (Version)object;
        return this.major == other.major && this.minor == other.minor && this.micro == other.micro;
    }

    /**
     * Compares this {@link Version} object to another.
     *
     * A version is considered to be less than another version if its
     * major component is less than the other version's major component, or the
     * major components are equal and its minor component is less than the other
     * version's minor component, or the major and minor components are equal
     * and its micro component is less than the other version's micro component.
     *
     * A version is considered to be equal to another version if the
     * major, minor and micro components are equal.
     *
     * @param other the {@link Version} object to be compared to this version.
     * @return A negative integer, zero, or a positive integer if this object is
     *         less than, equal to, or greater than the specified {@link Version} object.
     * @throws ClassCastException if the specified object is not a {@link Version}.
     */
    @Override
    public int compareTo(Version other) {
        if (this == other) return 0;

        int comparison = Integer.compare(getMajor(), other.getMajor());
        if (comparison != 0) {
            return comparison;
        }

        comparison = Integer.compare(getMinor(), other.getMinor());
        if (comparison != 0) {
            return comparison;
        }

        return Integer.compare(getMicro(), other.getMicro());
    }
}
