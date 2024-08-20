// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <vector>

namespace searchcorespi {
namespace index {

/**
 * Class used to log various events related to disk index handling.
 **/
struct EventLogger {
    static void diskIndexLoadStart(const std::string &indexDir);
    static void diskIndexLoadComplete(const std::string &indexDir,
                                      int64_t elapsedTimeMs);
    static void diskFusionStart(const std::vector<std::string> &sources,
                                const std::string &fusionDir);
    static void diskFusionComplete(const std::string &fusionDir,
                                   int64_t elapsedTimeMs);
};

} // namespace index
} // namespace searchcorespi

