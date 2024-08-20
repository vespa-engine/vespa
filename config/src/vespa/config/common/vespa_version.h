// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>

namespace config {

/**
 * Represents a version used in config protocol
 **/
struct VespaVersion
{
public:
    static VespaVersion fromString(const std::string & versionString);
    static const VespaVersion & getCurrentVersion();
    VespaVersion(const VespaVersion & version);
    VespaVersion &operator=(const VespaVersion &rhs);
    const std::string & toString() const;
private:
    VespaVersion(const std::string & versionString);
    std::string _versionString;
};

} // namespace config

