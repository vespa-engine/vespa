// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "vespa_version.h"

#ifndef V_TAG_COMPONENT
#define V_TAG_COMPONENT "1.0.0"
#endif

namespace config {

const VespaVersion currentVersion(VespaVersion::fromString(vespalib::string(V_TAG_COMPONENT)));


VespaVersion::VespaVersion(const VespaVersion & vespaVersion)
    : _versionString(vespaVersion._versionString)
{
}

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
