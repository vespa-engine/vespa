// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "vespa_version.h"
#include <vespa/vespalib/component/vtag.h>

namespace config {

const VespaVersion currentVersion(VespaVersion::fromString(vespalib::string(vespalib::VersionTagComponent)));


VespaVersion::VespaVersion(const VespaVersion & vespaVersion) = default;

VespaVersion & VespaVersion::operator=(const VespaVersion &rhs) = default;

VespaVersion::VespaVersion(const vespalib::string & versionString)
    : _versionString(versionString)
{
}

VespaVersion
VespaVersion::fromString(const vespalib::string & versionString)
{
    return VespaVersion(versionString);
}

const VespaVersion &
VespaVersion::getCurrentVersion() {
    return currentVersion;
}

const vespalib::string &
VespaVersion::toString() const
{
    return _versionString;
}

}
