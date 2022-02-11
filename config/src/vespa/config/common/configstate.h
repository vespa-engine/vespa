// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "misc.h"

namespace config {

/**
 * A config state represents the current state of a config instance
 */
struct ConfigState
{
public:
    ConfigState()
        : xxhash64(""),
          generation(0),
          applyOnRestart(false)
    { }
    ConfigState(const vespalib::string & xxhash, int64_t gen, bool _applyOnRestart)
        : xxhash64(xxhash),
          generation(gen),
          applyOnRestart(_applyOnRestart)
    { }

    vespalib::string xxhash64;
    int64_t generation;
    bool applyOnRestart;

    bool isNewerGenerationThan(const ConfigState & other) const {
        return isGenerationNewer(generation, other.generation);
    }

    bool hasDifferentPayloadFrom(const ConfigState & other) const {
        return (xxhash64.compare(other.xxhash64) != 0);
    }
};

} // namespace config
