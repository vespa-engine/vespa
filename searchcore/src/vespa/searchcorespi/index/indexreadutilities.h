// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fusionspec.h"
#include <vespa/searchlib/common/serialnum.h>
#include <string>

namespace searchcorespi {
namespace index {

/**
 * Utility class with functions to read aspects of an index from disk.
 * Used by the index maintainer.
 */
struct IndexReadUtilities {
    static FusionSpec readFusionSpec(const std::string &baseDir);
    static search::SerialNum readSerialNum(const std::string &dir);
};

} // namespace index
} // namespace searchcorespi


