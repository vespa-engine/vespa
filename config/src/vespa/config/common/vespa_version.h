// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace config {

/**
 * Represents a version used in config protocol
 **/
struct VespaVersion
{
public:
    static VespaVersion fromString(const vespalib::string & versionString);
    static const VespaVersion & getCurrentVersion();
    VespaVersion(const VespaVersion & version);
    VespaVersion &operator=(const VespaVersion &rhs);
    const vespalib::string & toString() const;
private:
    VespaVersion(const vespalib::string & versionString);
    vespalib::string _versionString;
};

} // namespace config

