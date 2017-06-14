// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "process_memory_stats.h"
#include <fstream>
#include <sstream>

namespace vespalib
{

namespace {

/*
 * Check if line specifies an address range.
 *
 * address           perms offset  dev   inode   pathname
 *
 * 00400000-00420000 r-xp 00000000 fd:04 16545041                           /usr/bin/less
 */

bool isRange(const std::string &line) {
    for (char c : line) {
        if (c == ' ') {
            return true;
        }
        if (c == ':') {
            return false;
        }
    }
    return false;
}


/*
 * Check if address range is anonymous, e.g. not mapped from file.
 * inode number is 0 in that case.
 *
 * address           perms offset  dev   inode   pathname
 *
 * 00400000-00420000 r-xp 00000000 fd:04 16545041                           /usr/bin/less
 * 00625000-00628000 rw-p 00000000 00:00 0
 *
 * The range starting at 00400000 is not anonymous.
 * The range starting at 00625000 is anonymous.
 */

bool isAnonymous(const std::string &line) {
    int delims = 0;
    for (char c : line) {
        if (delims >= 4) {
            return (c == '0');
        }
        if (c == ' ') {
            ++delims;
        }
    }
    return true;
}


/*
 * Lines not containing an address range contains a header and a
 * value, e.g.
 *
 * Size:                128 kB
 * Rss:                  96 kB
 * Anonymous:             0 kB
 *
 * The lines with header Anonymous are ignored, thus anonymous pages
 * caused by mmap() of a file with MAP_PRIVATE flags are counted as
 * mapped pages.
 */

std::string getLineHeader(const std::string &line)
{
    size_t len = 0;
    for (char c : line) {
        if (c == ':') {
            return line.substr(0, len);
        }
        ++len;
    }
    abort();
}

}


ProcessMemoryStats::ProcessMemoryStats()
    : _mapped_virt(0),
      _mapped_rss(0),
      _anonymous_virt(0),
      _anonymous_rss(0),
      _mappings_count(0)
{
}

ProcessMemoryStats::ProcessMemoryStats(uint64_t mapped_virt,
                                       uint64_t mapped_rss,
                                       uint64_t anonymous_virt,
                                       uint64_t anonymous_rss,
                                       uint64_t mappings_cnt)
    : _mapped_virt(mapped_virt),
      _mapped_rss(mapped_rss),
      _anonymous_virt(anonymous_virt),
      _anonymous_rss(anonymous_rss),
      _mappings_count(mappings_cnt)
{
}

ProcessMemoryStats
ProcessMemoryStats::create()
{
    ProcessMemoryStats ret;
    std::ifstream smaps("/proc/self/smaps");
    std::string line;
    std::string lineHeader;
    bool anonymous = true;
    uint64_t lineVal = 0;
    while (smaps.good()) {
        std::getline(smaps, line);
        if (isRange(line)) {
            ret._mappings_count += 1;
            anonymous = isAnonymous(line);
        } else if (!line.empty()) {
            lineHeader = getLineHeader(line);
            std::istringstream is(line.substr(lineHeader.size() + 1));
            is >> lineVal;
            if (lineHeader == "Size") {
                if (anonymous) {
                    ret._anonymous_virt += lineVal * 1024;
                } else {
                    ret._mapped_virt += lineVal * 1024;
                }
            } else if (lineHeader == "Rss") {
                if (anonymous) {
                    ret._anonymous_rss += lineVal * 1024;
                } else {
                    ret._mapped_rss += lineVal * 1024;
                }
            }
        }
    }
    return ret;
}

} // namespace vespalib
