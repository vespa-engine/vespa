// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "version.h"

namespace vespalib {

/**
 * @brief A component version specification.
 *
 * Version specifications have four components.
 * <ol>
 * <li>Major version. A non-negative integer.</li>
 * <li>Minor version. A non-negative integer.</li>
 * <li>Micro version. A non-negative integer.</li>
 * <li>Qualifier. An ascii text string. See <code>Version(String)</code> for the
 * format of the qualifier string.</li>
 * </ol>
 * <p>
 * An unspecified component is equivalent to 0 (or the empty string for qualifier).<br>
 * <p>
 * <code>VersionSpecification</code> objects are immutable.
 *
 * @author arnej27959
 * @author bratseth
 **/
class VersionSpecification
{
public:
    using string = Version::string;
private:
    int _major;
    int _minor;
    int _micro;
    string _qualifier;
    string _stringValue;

    /**
     * Must be called on construction after the component values are set.
     * Creates the underlying version string, and verifies that values are sound.
     */
    void initialize();
    /**
     * Is called after initialization if there are problems.
     * @throws IllegalArgumentException If the numerical components are negative
     *         or the qualifier string is invalid.
     */
    void verifySanity() __attribute__((noinline));

public:
    /** @brief constant signifying an unspecified component */
    enum {
        UNSPECIFIED = -1
    };

    /**
     * @brief Creates a version specification from the specified components.
     *
     * @param major major component of the version specification
     * @param minor minor component of the version specification
     * @param micro micro component of the version specification
     * @param qualifier Qualifier component of the version specification
     * @throws IllegalArgumentException if the numerical components are negative
     *         the qualifier string contains non-word/digit-characters, or
     *         an earlier component is not specified but a later one is
     */

    VersionSpecification(int major = UNSPECIFIED, int minor = UNSPECIFIED,
                         int micro = UNSPECIFIED, const string & qualifier = "");
    VersionSpecification(const VersionSpecification &);
    ~VersionSpecification();

    VersionSpecification &operator=(const VersionSpecification &rhs);
    /**
     * @brief Creates a version specification from the specified string.
     *
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
     * @param versionString String representation of the version specification
     * @throws IllegalArgumentException If <code>version</code> is improperly
     *         formatted.
     */
    VersionSpecification(const string & versionString);

    /** @brief Returns the major component of this version, or 0 if not specified */
    int getMajor() const { return _major==UNSPECIFIED ? 0 : _major; }

    /** @brief Returns the minor component of this version, or 0 if not specified */
    int getMinor() const { return _minor==UNSPECIFIED ? 0 : _minor; }

    /** @brief Returns the micro component of this version, or 0 if not specified */
    int getMicro() const { return _micro==UNSPECIFIED ? 0 : _micro; }

    /** @brief Returns the qualifier component of this version, or "" if not specified */
    const string & getQualifier() const { return _qualifier; }

    /** @brief Returns the specified major component, which may be UNSPECIFIED */
    int getSpecifiedMajor() const { return _major; }

    /** @brief Returns the specified minor component, which may be UNSPECIFIED */
    int getSpecifiedMinor() const { return _minor; }

    /** @brief Returns the specified micro component, which may be UNSPECIFIED */
    int getSpecifiedMicro() const { return _micro; }

    /**
     * @brief Returns the string representation of this version specification as major.minor.micro,
     * or major.minor.micro.qualifier if a non-empty qualifier was specified.
     */
    const string & toString() const { return _stringValue; }

    /**
     * @brief Compares this <code>VersionSpecification</code> to another.
     *
     * A versionspecification is considered to be <b>equal to </b> another versionspecification if the
     * major, minor and micro components are equal and the qualifier component
     * has the same value.
     * <p>
     *
     * @param other The <code>VersionSpecification</code> object to be compared.
     * @return true if equal, false otherwise
     */
    bool equals(const VersionSpecification& other) const;
    bool operator==(const VersionSpecification& other) const { return equals(other); }

    /**
     * @brief Compares this <code>VersionSpecification</code> object to another.
     *
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
     * has the same string value.
     *
     * @param other the <code>VersionSpecification</code> object compared.
     * @return A negative integer, zero, or a positive integer if this object is
     *         less than, equal to, or greater than the specified <code>VersionSpecification</code> object.
     * @throws ClassCastException if the specified object is not a <code>VersionSpecification</code>.
     */
    int compareTo(const VersionSpecification& other) const;
    bool operator<(const VersionSpecification& other) const { return compareTo(other) < 0; }


    /**
     * @brief Returns true if the given Version matches this specification.
     *
     * A Version identfier matches if all the numeric components
     * specified are the same as in the version, and qualifiers
     * are either both empty or set to the same value.  I.e, a version which
     * includes a qualifier will only match exactly and will never
     * return true from a request for an unspecified qualifier.
     */
    bool matches(const Version& version) const;

private:
    bool matches(int spec, int v) const;
};

} // namespace vespalib

