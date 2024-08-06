// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config-open-telemetry.h>

class FileWatcher {
    struct FileInfo {
        vespalib::string pathName;
        time_t seenModTime;

#if !defined(__cpp_aggregate_paren_init)
        // P0960R3 is supported by gcc >= 10, Clang >= 16 and AppleClang >= 16

        FileInfo(vespalib::string pathName_in, time_t seenModTime_in)
            : pathName(std::move(pathName_in)),
              seenModTime(seenModTime_in)
        {
        }
#endif
    };
    std::vector<FileInfo> watchedFiles;
public:
    bool anyChanged();
    void init(const config::StringVector &pathList);
};
