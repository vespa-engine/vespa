// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "process_memory_stats.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <algorithm>
#include <vector>
#include <cinttypes>
#include <vespa/vespalib/util/size_literals.h>

#include <vespa/log/log.h>

LOG_SETUP(".vespalib.util.process_memory_stats");

namespace vespalib {

namespace {

#ifdef __linux__
/*
 * Check if line specifies an address range.
 *
 * address           perms offset  dev   inode   pathname
 *
 * 00400000-00420000 r-xp 00000000 fd:04 16545041                           /usr/bin/less
 */

bool
isRange(std::string_view line) {
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

bool
isAnonymous(std::string_view line) {
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

std::string_view
getLineHeader(std::string_view line)
{
    return line.substr(0, line.find(':'));
}
#endif

}

ProcessMemoryStats
ProcessMemoryStats::createStatsFromSmaps()
{
    ProcessMemoryStats ret;
#ifdef __linux__
    asciistream smaps = asciistream::createFromDevice("/proc/self/smaps");
    bool anonymous = true;
    uint64_t lineVal = 0;
    while (!smaps.eof()) {
        string backedLine = smaps.getline();
        std::string_view line(backedLine);
        if (isRange(line)) {
            ret._mappings_count += 1;
            anonymous = isAnonymous(line);
        } else if (!line.empty()) {
            std::string_view lineHeader = getLineHeader(line);
            if (lineHeader == "Size") {
                asciistream is(line.substr(lineHeader.size() + 1));
                is >> lineVal;
                if (anonymous) {
                    ret._anonymous_virt += lineVal * 1024;
                } else {
                    ret._mapped_virt += lineVal * 1024;
                }
            } else if (lineHeader == "Rss") {
                asciistream is(line.substr(lineHeader.size() + 1));
                is >> lineVal;
                if (anonymous) {
                    ret._anonymous_rss += lineVal * 1024;
                } else {
                    ret._mapped_rss += lineVal * 1024;
                }
            }
        }
    }
#endif
    return ret;
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

namespace {

bool
similar(uint64_t lhs, uint64_t rhs, double epsilon)
{
    uint64_t maxDiff = std::max(1_Mi, uint64_t(epsilon * (lhs+rhs)/2.0));
    return (lhs < rhs) ? ((rhs - lhs) <= maxDiff) : ((lhs - rhs) <= maxDiff);
}

}

bool
ProcessMemoryStats::similarTo(const ProcessMemoryStats &rhs, double epsilon) const
{
    return similar(_mapped_virt, rhs._mapped_virt, epsilon) &&
           similar(_mapped_rss, rhs._mapped_rss, epsilon) &&
           similar(_anonymous_virt, rhs._anonymous_virt, epsilon) &&
           similar(_anonymous_rss, rhs._anonymous_rss, epsilon) &&
           (_mappings_count == rhs._mappings_count);
}

vespalib::string
ProcessMemoryStats::toString() const
{
    vespalib::asciistream stream;
    stream << "_mapped_virt=" << _mapped_virt << ", "
           << "_mapped_rss=" << _mapped_rss << ", "
           << "_anonymous_virt=" << _anonymous_virt << ", "
           << "_anonymous_rss=" << _anonymous_rss << ", "
           << "_mappings_count=" << _mappings_count;
    return stream.str();
}

ProcessMemoryStats
ProcessMemoryStats::create(double epsilon)
{
    constexpr size_t NUM_TRIES = 3;
    std::vector<ProcessMemoryStats> samples;
    samples.reserve(NUM_TRIES + 1);
    samples.push_back(createStatsFromSmaps());
    for (size_t i = 0; i < NUM_TRIES; ++i) {
        samples.push_back(createStatsFromSmaps());
        if (samples.back().similarTo(*(samples.rbegin()+1), epsilon)) {
            return samples.back();
        }
        LOG(debug, "create(): Memory stats have changed, trying to read smaps file again: i=%zu, prevStats={%s}, currStats={%s}",
            i, (samples.rbegin()+1)->toString().c_str(), samples.back().toString().c_str());
    }
    std::sort(samples.begin(), samples.end());
    LOG(debug, "We failed to find 2 consecutive samples that where similar with epsilon of %d%%.\nSmallest is '%s',\n median is '%s',\n largest is '%s'",
        int(epsilon*1000), samples.front().toString().c_str(), samples[samples.size()/2].toString().c_str(), samples.back().toString().c_str());
    return samples[samples.size()/2];
}

}
