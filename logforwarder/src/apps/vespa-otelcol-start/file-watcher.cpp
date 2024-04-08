// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "file-watcher.h"

#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>

namespace {

time_t lastModTime(const vespalib::string &fn) {
    if (fn.empty()) return 0;
    struct stat info;
    if (stat(fn.c_str(), &info) != 0) return 0;
    return info.st_mtime;
}

} // namespace

bool FileWatcher::anyChanged() {
    bool result = false;
    for (auto &entry : watchedFiles) {
        time_t updated = lastModTime(entry.pathName);
        if (updated != entry.seenModTime) {
            result = true;
            entry.seenModTime = updated;
        }
    }
    return result;
}

void FileWatcher::init(const config::StringVector &pathList) {
    watchedFiles.clear();
    for (const auto& path : pathList) {
        watchedFiles.emplace_back(path, lastModTime(path));
    }
}
