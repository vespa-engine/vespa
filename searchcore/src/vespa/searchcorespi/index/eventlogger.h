// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace searchcorespi {
namespace index {

/**
 * Class used to log various events related to disk index handling.
 **/
struct EventLogger {
    static void diskIndexLoadStart(const vespalib::string &indexDir);
    static void diskIndexLoadComplete(const vespalib::string &indexDir,
                                      int64_t elapsedTimeMs);
    static void diskFusionStart(const std::vector<vespalib::string> &sources,
                                const vespalib::string &fusionDir);
    static void diskFusionComplete(const vespalib::string &fusionDir,
                                   int64_t elapsedTimeMs);
};

} // namespace index
} // namespace searchcorespi

