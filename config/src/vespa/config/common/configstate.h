// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include "misc.h"

namespace config {

/**
 * A config state represents the current state of a config instance
 */
struct ConfigState
{
public:
    ConfigState()
        : md5(""),
          generation(0),
          internalRedeploy(false)
    { }
    ConfigState(const vespalib::string & md5sum, int64_t gen, bool value)
        : md5(md5sum),
          generation(gen),
          internalRedeploy(value)
    { }

    vespalib::string md5;
    int64_t generation;
    bool internalRedeploy;

    bool isNewerGenerationThan(const ConfigState & other) const {
        return isGenerationNewer(generation, other.generation);
    }

    bool hasDifferentPayloadFrom(const ConfigState & other) const {
        return (md5.compare(other.md5) != 0);
    }
};

} // namespace config
