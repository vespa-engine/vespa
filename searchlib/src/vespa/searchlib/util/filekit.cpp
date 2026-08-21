// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "filekit.h"

#include <sys/stat.h>
#include <unistd.h>

#include <cerrno>
#include <cstdint>

namespace search {

namespace {
constexpr uint64_t ONE_G = 1000 * 1000 * 1000;
} // namespace

vespalib::system_time FileKit::getModificationTime(const std::string& name) {
    struct stat stbuf{};
    int         lstatres;

    do {
        lstatres = lstat(name.c_str(), &stbuf);
    } while (lstatres == -1 && errno == EINTR);
    if (lstatres == 0) {
        uint64_t modtime_ns = stbuf.st_mtime * ONE_G;
#ifdef __linux__
        modtime_ns += stbuf.st_mtim.tv_nsec;
#elif defined(__APPLE__)
        modtime_ns += stbuf.st_mtimespec.tv_nsec;
#endif
        return vespalib::system_time(
            std::chrono::duration_cast<vespalib::system_time::duration>(std::chrono::nanoseconds(modtime_ns)));
    } else {
        return vespalib::system_time();
    }
}

} // namespace search
