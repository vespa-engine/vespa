// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component;

/**
 * A component version specification.
 * <p>
 * Version specifications have four components.
 * <ol>
 * <li>Major version. A non-negative integer.</li>
 * <li>Minor version. A non-negative integer.</li>
 * <li>Micro version. A non-negative integer.</li>
 * <li>Qualifier. An ascii text string. See <code>Version(String)</code> for the
 * format of the qualifier string.</li>
 * </ol>
 * <p>
 * A null version component means "unspecified", i.e any value of that
 * component matches the specification
 * <p>
 * <code>VersionSpecification</code> objects are immutable.
 *
 * @author arnej27959
 * @author bratseth
 */

public final class VersionSpecification implements Comparable<VersionSpecification> {

    private Integer major = null;
    private Integer minor = null;
    private Integer micro = null;
    private String  qualifier = null;

    private String stringValue;

    /** The empty version */
    public static final VersionSpecification emptyVersionSpecification = new VersionSpecification();

    /** Creates an empty version */
    public VersionSpecification() {
        this(null, null, null, null);
    }

    /**
     * Creates a version specification from the specified numerical components.
     *
     * @param major major component of the version specification, or null if not specified
     * @throws IllegalArgumentException If the numerical components are
     *         negative.
     */
    public VersionSpecification(Integer major) {
        this(major, null, null, null);
    }

    /**
     * Creates a version specification from the specified numerical components.
     *
     * @param major major component of the version specification, or null if not specified
     * @param minor minor component of the version specification, or null if not specified
     * @throws IllegalArgumentException If the numerical components are
     *         negative.
     */
    public VersionSpecification(Integer major, Integer minor) {
        this(major, minor, null, null);
    }

    /**
     * Creates a version specification from the specified numerical components.
     *
     * @param major major component of the version specification, or null if not specified
     * @param minor minor component of the version specification, or null if not specified
     * @param micro micro component of the version specification, or null if not specified
     * @throws IllegalArgumentException If the numerical components are
     *         negative.
     */
    public VersionSpecification(Integer major, Integer minor, Integer micro) {
        this(major, minor, micro, null);
    }

    /**
     * Creates a version specification from the specifed components.
     *
     * @param major major component of the version specification, or null if not specified
     * @param minor minor component of the version specification, or null if not specified
     * @param micro micro component of the version specification, or null if not specified
     * @param qualifier Qualifier component of the version specification, or null if not specified
     * @throws IllegalArgumentException if the numerical components are negative
     *         the qualifier string contains non-word/digit-characters, or
     *         an earlier component is not specified but a later one is
     */
    public VersionSpecification(Integer major, Integer minor, Integer micro, String qualifier) {
        this.major = major;
        this.minor = minor;
        this.micro = micro;
        this.qualifier = qualifier;
        initialize();
    }

    /**
     * Creates a version specification from the specified string.
     *
     * <p>
     * VersionSpecification strings follows this grammar (same as Osgi versions):
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
     * @param versionString String representation of the version specification
     * @throws IllegalArgumentException If <code>version</code> is improperly
     *         formatted.
     */
    public VersionSpecification(String versionString) {
        if (! "".equals(versionString)) {
            String[] components = versionString.split("\\x2e"); // Split on dot

            if (components.length > 0) {
                String s = components[0];
                if (! s.equals("*")) {
                    major = new Integer(s);
                }
            }
            if (components.length > 1) {
                String s = components[1];
                if (! s.equals("*")) {
                    minor = new Integer(s);
                }
            }
            if (components.length > 2) {
                String s = components[2];
                if (! s.equals("*")) {
                    micro = new Integer(s);
                }
            }
            if (components.length > 3) {
                qualifier = components[3];
            }
            if (components.length > 4)
                throw new IllegalArgumentException("Too many components in " + versionString);
        }
        initialize();
    }

    public static VersionSpecification fromString(String versionString) {
        if (versionString == null) {
            return emptyVersionSpecification;
        } else {
            return new VersionSpecification(versionString);
        }
    }

    /**
     * Must be called on construction after the component values are set
     *
     * @throws IllegalArgumentException If the numerical components are negative
     *         or the qualifier string is invalid.
     */
    private void initialize() {
        stringValue = toStringValue(major, minor, micro, qualifier);
        ensureUnspecifiedOnlyToTheRight(major, minor, micro, qualifier);

        if (major!=null && major < 0)
            throw new IllegalArgumentException("Negative major in " + this);
        if (minor!=null && minor < 0)
            throw new IllegalArgumentException("Negative minor in " + this);
        if (micro!=null && micro < 0)
            throw new IllegalArgumentException("Negative micro in " + this);

        String q = getQualifier();
        for (int i = 0; i < q.length(); i++) {
            if ( !Character.isLetterOrDigit(q.charAt(i)) )
                throw new IllegalArgumentException("Invalid qualifier in " + this +
                                                   ": Invalid character at position " + i + " in qualifier");
        }
    }

    private String toStringValue(Object... objects) {
        StringBuilder b = new StringBuilder();
        boolean first = true;
        for (Object o : objects) {
            if (o != null)  {
                if (! first)
                    b.append(".");
                b.append(o);
                first = false;
            }
        }
        return b.toString();
    }


    private void ensureUnspecifiedOnlyToTheRight(Object... objects) {
        boolean restMustBeNull=false;
        for (Object o : objects) {
            if (restMustBeNull && o!=null)
                throw new IllegalArgumentException("A component to the left of a specified is unspecified in " + this);
            if (o==null)
                restMustBeNull=true;
        }
    }

    /** Returns the major component of this version, or 0 if not specified */
    public int getMajor() { return major!=null ? major : 0; }

    /** Returns the minor component of this version, or 0 if not specified */
    public int getMinor() { return minor!=null ? minor : 0; }

    /** Returns the micro component of this version, or 0 if not specified */
    public int getMicro() { return micro!=null ? micro : 0; }

    /** Returns the qualifier component of this version, or "" if not specified */
    public String getQualifier() { return qualifier!=null ? qualifier : ""; }

    /** Returns the specified major component, which may be null */
    public Integer getSpecifiedMajor() { return major; }

    /** Returns the specified minor component, which may be null */
    public Integer getSpecifiedMinor() { return minor; }

    /** Returns the specified micro component, which may be null */
    public Integer getSpecifiedMicro() { return micro; }

    /** Returns the specified qualifier component, which may be null */
    public String getSpecifiedQualifier() { return qualifier; }

    /**
     * Returns the string representation of this version specification as major.minor.micro.qualifier, where
     * trailing unspecified components are omitted
     */
    public String toString() { return stringValue; }

    public int hashCode() { return stringValue.hashCode(); }

    /**
     * Compares this <code>VersionSpecification</code> to another.
     *
     * <p>
     * A version is considered to be <b>equal to </b> another version if the
     * major, minor and micro components are equal and the qualifier component
     * is equal (using <code>String.equals</code>).
     * <p>
     * Note that two versions are only equal if they are equally specified, use
     * {@link #matches} to match a more specified version to a less specified.
     *
     * @param object The <code>VersionSpecification</code> object to be compared.
     * @return <code>true</code> if <code>object</code> is a
     *         <code>VersionSpecification</code> and is equal to this object;
     *         <code>false</code> otherwise.
     */
    public boolean equals(Object object) {
        if (object == this) return true;

        if (!(object instanceof VersionSpecification)) return false;
        VersionSpecification other = (VersionSpecification) object;
        if ( ! equals(this.major,other.major)) return false;
        if ( ! equals(this.minor,other.minor)) return false;
        if ( ! equals(this.micro,other.micro)) return false;
        if ( ! equals(this.qualifier,other.qualifier)) return false;
        return true;
    }

    private boolean equals(Object o1,Object o2) {
        if (o1==null && o2==null) return true;
        if (o1==null || o2==null) return false;
        return o1.equals(o2);
    }

    /**
     * check if the Version specification is equal to the
     * empty spec ("match anything")
     **/
    public boolean isEmpty() {
        return equals(emptyVersionSpecification);
    }

    /**
     * Returns the lowest possible Version object that matches this spec
     **/
    public Version lowestMatchingVersion() {
        return new Version(getMajor(), getMinor(), getMicro(), getQualifier());
    }

    /**
     * Returns true if the given version matches this specification.
     * It matches if all the numeric components specified are the same
     * as in the version, and both qualifiers are either null or set
     * to the same value.  I.e, a version which includes a qualifier
     * will only match exactly and will never return true from a
     * request for an unspecified qualifier.
     */
    public boolean matches(Version version) {
        if (matches(this.major, version.getMajor()) &&
            matches(this.minor, version.getMinor()) &&
            matches(this.micro, version.getMicro()))
        {
            return (version.getQualifier().equals(this.getQualifier()));
        } else {
            return false;
        }
    }

    private boolean matches(Integer componentSpec, Integer component) {
        if (componentSpec == null) return true;
        return componentSpec.equals(component);
    }

    /**
     * Compares this <code>VersionSpecification</code> object to another.
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
     * @param other the <code>VersionSpecification</code> object to be compared.
     * @return A negative integer, zero, or a positive integer if this object is
     *         less than, equal to, or greater than the specified <code>VersionSpecification</code> object.
     * @throws ClassCastException if the specified object is not a <code>VersionSpecification</code>.
     */
    public int compareTo(VersionSpecification other) {
        int result = this.getMajor() - other.getMajor();
        if (result != 0) return result;

        result = this.getMinor() - other.getMinor();
        if (result != 0) return result;

        result = this.getMicro() - other.getMicro();
        if (result != 0) return result;

        return getQualifier().compareTo(other.getQualifier());
    }

    public VersionSpecification intersect(VersionSpecification other) {
        return new VersionSpecification(
                intersect(major, other.major, "major"),
                intersect(minor, other.minor, "minor"),
                intersect(micro, other.micro, "micro"),
                intersect(qualifier, other.qualifier, "qualifier"));
    }

    private <T> T intersect(T spec1, T spec2, String name) {
        if (spec1 == null) {
            return spec2;
        } else if (spec2 == null) {
            return spec1;
        } else if (spec1.equals(spec2)) {
            return spec1;
        } else {
            throw new RuntimeException("The " + name + " component does not match(" + spec1 + "!=" + spec2 + ").");
        }
    }
}
