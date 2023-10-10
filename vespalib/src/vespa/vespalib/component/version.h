// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {

/**
 * @brief A component version identifier.
 *
 * Version identifiers have four components.
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
 * <code>Version</code> objects are immutable.
 *
 * @author arnej27959
 * @author bratseth
 **/
class Version
{
public:
    using string = vespalib::string;
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
    /**
     * @brief Creates a version identifier from the specified components.
     *
     * @param major major component of the version identifier, 0 if not specified
     * @param minor minor component of the version identifier, 0 if not specified
     * @param micro micro component of the version identifier, 0 if not specified
     * @param qualifier Qualifier component of the version identifier, empty string if unspecified
     * @throws IllegalArgumentException if the numerical components are negative
     *         the qualifier string contains non-word/digit-characters
     */

    Version(int major = 0, int minor = 0, int micro = 0, const string & qualifier = "");
    Version(const Version &);
    Version & operator = (const Version &);
    ~Version();

    /**
     * @brief Creates a version identifier from the specified string.
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
     * @param versionString String representation of the version identifier
     * @throws IllegalArgumentException If <code>version</code> is improperly
     *         formatted.
     */
    Version(const string & versionString);

    /** @brief Returns the major component of this version, or 0 if not specified */
    int getMajor() const { return _major; }

    /** @brief Returns the minor component of this version, or 0 if not specified */
    int getMinor() const { return _minor; }

    /** @brief Returns the micro component of this version, or 0 if not specified */
    int getMicro() const { return _micro; }

    /** @brief Returns the qualifier component of this version, or "" if not specified */
    const string &  getQualifier() const { return _qualifier; }

    /**
     * @brief Returns the string representation of this version identifier as major.minor.micro,
     * or major.minor.micro.qualifier if a non-empty qualifier was specified.
     */
    const string & toString() const { return _stringValue; }

    /**
     * @brief Compares this Version object to another.
     *
     * A version is considered to be <b>equal to </b> another version if the
     * major, minor and micro components are equal and the qualifier component
     * has the same value.
     *
     * @param other The <code>Version</code> object to be compared.
     * @return true if equal, false otherwise
     */
    bool equals(const Version& other) const;
    bool operator==(const Version& other) const { return equals(other); }

    /**
     * @brief Compares this Version object to another.
     *
     * A version is considered to be <b>less than </b> another version if its
     * major component is less than the other version's major component, or the
     * major components are equal and its minor component is less than the other
     * version's minor component, or the major and minor components are equal
     * and its micro component is less than the other version's micro component,
     * or the major, minor and micro components are equal and it's qualifier
     * component is less than the other version's qualifier component (using
     * <code>string.compare</code>).
     *
     * A version is considered to be <b>equal to</b> another version
     * if the major, minor and micro components are equal and the
     * qualifier component has the same string value.
     *
     * @param other the <code>Version</code> object compared.
     * @return A negative integer, zero, or a positive integer if this object is
     *         less than, equal to, or greater than the specified <code>Version</code> object.
     * @throws ClassCastException if the specified object is not a <code>Version</code>.
     */
    int compareTo(const Version& other) const;
    bool operator<(const Version& other) const { return compareTo(other) < 0; }

};

} // namespace vespalib

