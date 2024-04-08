// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config-open-telemetry.h>

class FileWatcher {
    struct FileInfo {
        vespalib::string pathName;
        time_t seenModTime;
    };
    std::vector<FileInfo> watchedFiles;
public:
    bool anyChanged();
    void init(const config::StringVector &pathList);
};
