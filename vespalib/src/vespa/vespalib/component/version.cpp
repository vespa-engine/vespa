// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "version.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <climits>

namespace vespalib {

Version::Version(int major, int minor, int micro, const string & qualifier)
    : _major(major),
      _minor(minor),
      _micro(micro),
      _qualifier(qualifier),
      _stringValue()
{
    initialize();
}

Version::Version(const Version &) = default;
Version & Version::operator = (const Version &) = default;
Version::~Version() = default;

void
Version::initialize()
{
    asciistream buf;
    if (_qualifier != "") {
        buf << _major << "." << _minor << "." << _micro << "." << _qualifier;
    } else if (_micro > 0) {
        buf << _major << "." << _minor << "." << _micro;
    } else if (_minor > 0) {
        buf << _major << "." << _minor;
    } else if (_major > 0) {
        buf << _major;
    }
    _stringValue = buf.str();
    if ((_major < 0) || (_minor < 0) || (_micro < 0) || !_qualifier.empty()) {
        verifySanity();
    }
}

void
Version::verifySanity()
{
    if (_major < 0)
        throw IllegalArgumentException("Negative major in " + _stringValue);

    if (_minor < 0)
        throw IllegalArgumentException("Negative minor in " + _stringValue);

    if (_micro < 0)
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

// Precondition: input.empty() == false
static int parseInteger(stringref input) __attribute__((noinline));
static int parseInteger(stringref input)
{
    const char *s = input.data();
    unsigned char firstDigit = s[0];
    if (!isdigit(firstDigit))
        throw IllegalArgumentException("integer must start with a digit");
    char *ep;
    long ret = strtol(s, &ep, 10);
    if (ret > INT_MAX || ret < 0) {
        throw IllegalArgumentException("integer out of range");
    }
    if (s + input.size() != ep ) {
        throw IllegalArgumentException("extra characters after integer");
    }
    return ret;
}


Version::Version(const string & versionString)
    : _major(0),
      _minor(0),
      _micro(0),
      _qualifier(),
      _stringValue(versionString)
{
    if ( ! versionString.empty()) {
        stringref r(versionString.c_str(), versionString.size());
        stringref::size_type dot(r.find('.'));
        stringref majorS(r.substr(0, dot)); 

        if ( !majorS.empty()) {
            _major = parseInteger(majorS);
            if (dot == stringref::npos) return;
            r = r.substr(dot + 1);
            dot = r.find('.');
            stringref minorS(r.substr(0, dot)); 
            if ( !minorS.empty()) {
                _minor = parseInteger(minorS);

                if (dot == stringref::npos) return;
                r = r.substr(dot + 1);
                dot = r.find('.');
                stringref microS(r.substr(0, dot)); 
                if ( ! microS.empty()) {
                    _micro = parseInteger(microS);

                    if (dot == stringref::npos) return;
                    r = r.substr(dot + 1);
                    dot = r.find('.');
                    if (dot == stringref::npos) {
                        _qualifier = r; 
                    } else {
                        throw IllegalArgumentException("too many dot-separated components in version string '" + versionString + "'");
                    }
                }
            }
        }
    }
    verifySanity();
}

bool
Version::equals(const Version& other) const
{
    if (_major != other._major) return false;
    if (_minor != other._minor) return false;
    if (_micro != other._micro) return false;
    if (_qualifier != other._qualifier) return false;

    return true;
}

int
Version::compareTo(const Version& other) const
{
    int result = getMajor() - other.getMajor();
    if (result != 0) return result;

    result = getMinor() - other.getMinor();
    if (result != 0) return result;

    result = getMicro() - other.getMicro();
    if (result != 0) return result;

    return getQualifier().compare(other.getQualifier());
}

} // namespace vespalib
