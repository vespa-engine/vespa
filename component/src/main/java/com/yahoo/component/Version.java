// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component;

import com.yahoo.text.Utf8;
import com.yahoo.text.Utf8Array;

import java.nio.ByteBuffer;

/**
 * A component version
 * <p>
 * Version identifiers have four components.
 * <ol>
 * <li>Major version. A non-negative integer.</li>
 * <li>Minor version. A non-negative integer.</li>
 * <li>Micro version. A non-negative integer.</li>
 * <li>Qualifier. An ascii text string. See <code>Version(String)</code> for the
 * format of the qualifier string.</li>
 * </ol>
 *
 * <p>
 * Unspecified version component is equivalent to 0 (or the empty string for qualifier).
 *
 * <p>
 * <code>Version</code> objects are immutable.
 *
 * @author bratseth
 */
public final class Version implements Comparable<Version> {

    private int major = 0;
    private int minor = 0;
    private int micro = 0;
    private String qualifier = "";
    private String stringValue;

    /** The empty version */
    public static final Version emptyVersion = new Version();

    /** Creates an empty version */
    public Version() {
        this(0, 0, 0, null);
    }

    /**
     * Creates a version identifier from the specified numerical components.
     *
     * @param major major component of the version identifier
     * @throws IllegalArgumentException If the numerical components are
     *         negative.
     */
    public Version(int major) {
        this(major, 0, 0, null);
    }

    /**
     * Creates a version identifier from the specified numerical components.
     *
     * @param major major component of the version identifier
     * @param minor minor component of the version identifier
     * @throws IllegalArgumentException If the numerical components are
     *         negative.
     */
    public Version(int major, int minor) {
        this(major, minor, 0, null);
    }

    /**
     * Creates a version identifier from the specified numerical components.
     *
     * @param major major component of the version identifier
     * @param minor minor component of the version identifier
     * @param micro micro component of the version identifier
     * @throws IllegalArgumentException If the numerical components are
     *         negative.
     */
    public Version(int major, int minor, int micro) {
        this(major, minor, micro, null);
    }

    /**
     * Creates a version identifier from the specified components.
     *
     * @param major major component of the version identifier
     * @param minor minor component of the version identifier
     * @param micro micro component of the version identifier
     * @param qualifier Qualifier component of the version identifier, or null if not specified
     * @throws IllegalArgumentException if the numerical components are negative
     *         the qualifier string contains non-word/digit-characters, or
     *         an earlier component is not specified but a later one is
     */
    public Version(int major, int minor, int micro, String qualifier) {
        this.major = major;
        this.minor = minor;
        this.micro = micro;
        if (qualifier != null) this.qualifier = qualifier;
        stringValue = toStringValue();
        verify();
    }

    /**
     * Creates a version identifier from the specified string.
     *
     * <p>
     * Version strings follows this grammar (same as Osgi versions):
     *
     * <pre>
     * version ::= major('.'minor('.'micro('.'qualifier)?)?)?
     * major ::= digit+
     * minor ::= digit+
     * micro ::= digit+
     * qualifier ::= (alpha|digit|'_'|'-')+
     * digit ::= [0..9]
     * alpha ::= [a..zA..Z]
     * </pre>
     *
     * @param versionString String representation of the version identifier
     * @throws IllegalArgumentException If <code>version</code> is improperly formatted.
     */
    public Version(String versionString) {
        if (! "".equals(versionString)) {
            String[] components=versionString.split("\\."); // Split on dot

            if (components.length > 0)
                major = Integer.parseInt(components[0]);
            if (components.length > 1)
                minor = Integer.parseInt(components[1]);
            if (components.length > 2)
                micro = Integer.parseInt(components[2]);
            if (components.length > 3)
                qualifier = components[3];
            if (components.length > 4)
                throw new IllegalArgumentException("Too many components in '" + versionString + "'");
        }
        stringValue = toStringValue();
        verify();
    }

    static private int readInt(ByteBuffer bb) {
        int accum=0;
        for (int i=bb.remaining(); i > 0; i--) {
            byte b=bb.get();
            if (b >= 0x30 && b <= 0x39) {
                accum = accum * 10 + (b-0x30);
            } else if (b == 0x2e) {
                return accum;
            } else {
                throw new IllegalArgumentException("Failed decoding integer from utf8stream. Stream = " + bb.toString());
            }
        }
        return accum;
    }
    /**
     * Creates a version identifier from the specified string.
     *
     * <p>
     * Version strings follows this grammar (same as Osgi versions):
     *
     * <pre>
     * version ::= major('.'minor('.'micro('.'qualifier)?)?)?
     * major ::= digit+
     * minor ::= digit+
     * micro ::= digit+
     * qualifier ::= (alpha|digit|'_'|'-')+
     * digit ::= [0..9]
     * alpha ::= [a..zA..Z]
     * </pre>
     *
     * @param versionString String representation of the version identifier
     * @throws IllegalArgumentException If <code>version</code> is improperly
     *         formatted.
     */
    public Version(Utf8Array versionString) {
        ByteBuffer bb = versionString.wrap();
        if (bb.remaining() > 0) {
            major = readInt(bb);
            if (bb.remaining() > 0) {
                minor = readInt(bb);
                if (bb.remaining() > 0) {
                    micro = readInt(bb);
                    if (bb.remaining() > 0) {
                        qualifier = Utf8.toString(bb);
                    }
                }
            }
        } else {
            throw new IllegalArgumentException("Empty version specification");
        }

        stringValue = versionString.toString();
        verify();
    }

    /** Returns new Version(versionString), or Version.emptyVersion if the input string is null or "" */
    public static Version fromString(String versionString) {
        return (versionString == null) ? emptyVersion :new Version(versionString);
    }

    /**
     * Must be called on construction after the component values are set
     *
     * @throws IllegalArgumentException If the numerical components are negative
     *         or the qualifier string is invalid.
     */
    private void verify() {
        if (major < 0)
            throw new IllegalArgumentException("Negative major in " + this);
        if (minor < 0)
            throw new IllegalArgumentException("Negative minor in " + this);
        if (micro < 0)
            throw new IllegalArgumentException("Negative micro in " + this);

        for (int i = 0; i < qualifier.length(); i++) {
            char c = qualifier.charAt(i);
            if (!Character.isLetterOrDigit(c))
                throw new IllegalArgumentException("Invalid qualifier in " + this +
                                                   ": Invalid character at position " + i + " in qualifier");
        }
    }

    private String toStringValue() {
        StringBuilder b = new StringBuilder();
        if (! qualifier.isEmpty()) {
            b.append(getMajor()).append(".").append(getMinor()).append(".").append(getMicro()).append(".").append(qualifier);
        } else if (getMicro() != 0) {
            b.append(getMajor()).append(".").append(getMinor()).append(".").append(getMicro());
        } else if (getMinor() != 0) {
            b.append(getMajor()).append(".").append(getMinor());
        } else if (getMajor() != 0) {
            b.append(getMajor());
        }
        return b.toString();
    }

    /**
     * Returns the string representation of this version identifier as major.minor.micro.qualifier,
     * omitting .qualifier if qualifier empty or unspecified
     * <p>
     * This string form is part of the API of Version and will never change.
     */
    public String toFullString() {
        StringBuilder b = new StringBuilder();
        b.append(getMajor()).append(".").append(getMinor()).append(".").append(getMicro());

        if (! qualifier.isEmpty()) {
            b.append(".");
            b.append(qualifier);
        }

        return b.toString();
    }

    /** Returns the major component of this version, or 0 if not specified */
    public int getMajor() { return major; }

    /** Returns the minor component of this version, or 0 if not specified */
    public int getMinor() { return minor; }

    /** Returns the micro component of this version, or 0 if not specified */
    public int getMicro() { return micro; }

    /** Returns the qualifier component of this version, or "" if not specified */
    public String getQualifier() { return qualifier; }

    /**
     * Returns the string representation of this version identifier as major.minor.micro.qualifier,
     * omitting the remaining parts after reaching the first unspecified component.
     * Unspecified version component is equivalent to 0 (or the empty string for qualifier).
     * <p>
     * The string representation of a Version specified here is a part of the API and will never change.
     */
    @Override
    public String toString() { return stringValue; }

    @Override
    public int hashCode() { return stringValue.hashCode(); }

    /** Returns whether this equals the empty version */
    public boolean isEmpty() { return this.equals(emptyVersion); }
    
    /**
     * Compares this <code>Version</code> to another.
     *
     * <p>
     * A version is considered to be <b>equal to </b> another version if the
     * major, minor and micro components are equal and the qualifier component
     * is equal (using <code>String.equals</code>).
     * <p>
     *
     * @param object The <code>Version</code> object to be compared.
     * @return <code>true</code> if <code>object</code> is a
     *         <code>Version</code> and is equal to this object;
     *         <code>false</code> otherwise.
     */
    @Override
    public boolean equals(Object object) {
        if ( ! (object instanceof Version)) return false;
        Version other = (Version) object;
        if (this.major != other.major) return false;
        if (this.minor != other.minor) return false;
        if (this.micro != other.micro) return false;
        return (this.qualifier.equals(other.qualifier));
    }

    @SuppressWarnings("unused")
    private boolean equals(Object o1, Object o2) {
        if (o1 == null && o2 == null) return true;
        if (o1 == null || o2 == null) return false;
        return o1.equals(o2);
    }

    /**
     * Compares this <code>Version</code> object to another version.
     * <p>
     * A version is considered to be <b>less than </b> another version if its
     * major component is less than the other version's major component, or the
     * major components are equal and its minor component is less than the other
     * version's minor component, or the major and minor components are equal
     * and its micro component is less than the other version's micro component,
     * or the major, minor and micro components are equal and it's qualifier
     * component is less than the other version's qualifier component (using
     * <code>String.compareTo</code>).
     * <p>
     * A version is considered to be <b>equal to</b> another version if the
     * major, minor and micro components are equal and the qualifier component
     * is equal (using <code>String.compareTo</code>).
     * <p>
     * Unspecified numeric components are treated as 0, unspecified qualifier is treated as the empty string.
     *
     * @param other the <code>Version</code> object to be compared.
     * @return A negative integer, zero, or a positive integer if this object is
     *         less than, equal to, or greater than the specified <code>Version</code> object.
     * @throws ClassCastException if the specified object is not a <code>Version</code>.
     */
    @Override
    public int compareTo(Version other) {
        if (other == this) return 0;

        int result = this.getMajor() - other.getMajor();
        if (result != 0) return result;

        result = this.getMinor() - other.getMinor();
        if (result != 0) return result;

        result = this.getMicro() - other.getMicro();
        if (result != 0) return result;

        return getQualifier().compareTo(other.getQualifier());
    }

    /**
     * Returns whether this version number is strictly lower than the given version. This has the same semantics as
     * {@link this#compareTo}.
     */
    public boolean isBefore(Version other) {
        return compareTo(other) < 0;
    }

    /**
     * Returns whether this version number is strictly higher than the given version. This has the same semantics as
     * {@link this#compareTo}.
     */
    public boolean isAfter(Version other) {
        return compareTo(other) > 0;
    }

    /** Creates a version specification that only matches this version */
    public VersionSpecification toSpecification() {
        return (this == emptyVersion)
                ? VersionSpecification.emptyVersionSpecification
                : new VersionSpecification(getMajor(), getMinor(), getMicro(), getQualifier());
    }

}
