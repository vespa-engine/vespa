// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "versionspecification.h"
#include "version.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <cctype>
#include <climits>

namespace vespalib {

VersionSpecification::VersionSpecification(int major, int minor, int micro, const string & qualifier)
    : _major(major),
      _minor(minor),
      _micro(micro),
      _qualifier(qualifier),
      _stringValue()
{
    initialize();
}

VersionSpecification::VersionSpecification(const VersionSpecification &) = default;

VersionSpecification::~VersionSpecification() = default;

VersionSpecification &VersionSpecification::operator=(const VersionSpecification &rhs) = default;

void
VersionSpecification::initialize()
{
    asciistream buf;

    if (_major == UNSPECIFIED) { buf << "*"; } else { buf << _major; }
    buf << ".";
    if (_minor == UNSPECIFIED) { buf << "*"; } else { buf << _minor; }
    buf << ".";
    if (_micro == UNSPECIFIED) { buf << "*"; } else { buf << _micro; }

    if (_qualifier != "") {
        buf << "." << _qualifier;
    }
    _stringValue = buf.str();

    if ((_major < UNSPECIFIED) || (_minor < UNSPECIFIED) || (_micro < UNSPECIFIED) || !_qualifier.empty()) {
        verifySanity();
    }
}

void
VersionSpecification::verifySanity()
{

    if (_major < UNSPECIFIED)
        throw IllegalArgumentException("Negative major in " + _stringValue);

    if (_minor < UNSPECIFIED)
        throw IllegalArgumentException("Negative minor in " + _stringValue);

    if (_micro < UNSPECIFIED)
        throw IllegalArgumentException("Negative micro in " + _stringValue);

    if (_qualifier != "") {
        for (size_t i = 0; i < _qualifier.length(); i++) {
            unsigned char c = _qualifier[i];
            if (! isalnum(c)) {
                throw IllegalArgumentException("Error in " + _stringValue + ": Invalid character in qualifier");
            }
        }
    }
}

static int parseInteger(const VersionSpecification::string & input) __attribute__((noinline));
static int parseInteger(const VersionSpecification::string & input)
{
    const char *s = input.c_str();
    unsigned char firstDigit = s[0];
    if (!isdigit(firstDigit))
        throw IllegalArgumentException("integer must start with a digit");
    char *ep;
    long ret = strtol(s, &ep, 10);
    if (ret > INT_MAX || ret < 0) {
        throw IllegalArgumentException("integer out of range");
    }
    if (*ep != '\0') {
        throw IllegalArgumentException("extra characters after integer");
    }
    return ret;
}


VersionSpecification::VersionSpecification(const string & versionString)
    : _major(UNSPECIFIED),
      _minor(UNSPECIFIED),
      _micro(UNSPECIFIED),
      _qualifier(),
      _stringValue()
{
    if (versionString != "") {
        StringTokenizer components(versionString, ".", ""); // Split on dot

        if (components.size() > 0)
            _major = parseInteger(components[0]);
        if (components.size() > 1)
            _minor = parseInteger(components[1]);
        if (components.size() > 2)
            _micro = parseInteger(components[2]);
        if (components.size() > 3)
            _qualifier = components[3];
        if (components.size() > 4)
            throw IllegalArgumentException("too many dot-separated components in version string");
    }
    initialize();
}

bool
VersionSpecification::equals(const VersionSpecification& other) const
{
    if (_major != other._major) return false;
    if (_minor != other._minor) return false;
    if (_micro != other._micro) return false;
    if (_qualifier != other._qualifier) return false;

    return true;
}

int
VersionSpecification::compareTo(const VersionSpecification& other) const
{
    int result = _major - other._major;
    if (result != 0) return result;

    result = _minor - other._minor;
    if (result != 0) return result;

    result = _micro - other._micro;
    if (result != 0) return result;

    return _qualifier.compare(other._qualifier);
}

bool
VersionSpecification::matches(int spec, int v) const
{
    if (spec == VersionSpecification::UNSPECIFIED) return true;
    return (spec == v);
}

bool
VersionSpecification::matches(const Version& version) const
{
    if (matches(_major, version.getMajor()) &&
        matches(_minor, version.getMinor()) &&
        matches(_micro, version.getMicro()))
    {
        return getQualifier() == version.getQualifier();
    } else {
        return false;
    }
}


} // namespace vespalib
