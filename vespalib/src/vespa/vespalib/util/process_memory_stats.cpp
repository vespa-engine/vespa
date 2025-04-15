// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "process_memory_stats.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <algorithm>
#include <vector>
#include <cinttypes>
#include <vespa/vespalib/util/size_literals.h>

#include <vespa/log/log.h>

#include "exceptions.h"

LOG_SETUP(".vespalib.util.process_memory_stats");

namespace vespalib {

size_t ProcessMemoryStats::PAGE_SIZE = sysconf(_SC_PAGESIZE);

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
        std::string backedLine = smaps.getline();
        std::string_view line(backedLine);
        if (isRange(line)) {
            anonymous = isAnonymous(line);
        } else if (!line.empty()) {
            std::string_view lineHeader = getLineHeader(line);
            if (lineHeader == "Size") {
                asciistream is(line.substr(lineHeader.size() + 1));
                is >> lineVal;
                ret._virt += lineVal * 1024;
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

/*
 * The statm line looks like this:
 * size resident shared text lib data dt
 *
 * Example:
 * 3332000 1917762 8060 1 0 2960491 0
 *
 * The numbers specify the numbers of pages
 */
ProcessMemoryStats
ProcessMemoryStats::createStatsFromStatm()
{
    ProcessMemoryStats ret;
#ifdef __linux__
    asciistream statm = asciistream::createFromDevice("/proc/self/statm");
    ret = parseStatm(statm);
#endif
    return ret;
}


ProcessMemoryStats
ProcessMemoryStats::parseStatm(asciistream &statm)
{
    ProcessMemoryStats ret;
    try {
        // the first three values in smaps are size, resident, and shared
        // the values in statm are measured in numbers of pages
        uint64_t size, resident, shared;
        statm >> size >> resident >> shared;

        // we only get the total program size via smaps (no distinction between anonymous and non-anonymous)
        // VmSize (in status) = size (in smaps)
        ret._virt = size * PAGE_SIZE;

        // RssAnon (in status) = resident - shared (in smaps)
        ret._anonymous_rss = (resident - shared) * PAGE_SIZE;

        // RssFile + RssShmem (in status) = shared (in smaps)
        ret._mapped_rss = shared * PAGE_SIZE;

    } catch (const IllegalArgumentException& e) {
        LOG(warning, "Error '%s' while reading statm line '%s'", e.what(), statm.c_str());
    }

    return ret;
}

ProcessMemoryStats ProcessMemoryStats::sample(SamplingStrategy strategy) {
    switch (strategy) {
    case SMAPS:
        return createStatsFromSmaps();
    default:
        return createStatsFromStatm();
    }
}

ProcessMemoryStats::ProcessMemoryStats()
    : _virt(0),
      _mapped_rss(0),
      _anonymous_rss(0)
{
}

ProcessMemoryStats::ProcessMemoryStats(uint64_t virt,
                                       uint64_t mapped_rss,
                                       uint64_t anonymous_rss)
    : _virt(virt),
      _mapped_rss(mapped_rss),
      _anonymous_rss(anonymous_rss)
{
}

namespace {

bool
similar(uint64_t lhs, uint64_t rhs, double epsilon)
{
    uint64_t maxDiff = std::max(uint64_t(1_Mi), uint64_t(epsilon * (lhs+rhs)/2.0));
    return (lhs < rhs) ? ((rhs - lhs) <= maxDiff) : ((lhs - rhs) <= maxDiff);
}

}

bool
ProcessMemoryStats::similarTo(const ProcessMemoryStats &rhs, double epsilon) const
{
    return similar(_virt, rhs._virt, epsilon) &&
           similar(_mapped_rss, rhs._mapped_rss, epsilon) &&
           similar(_anonymous_rss, rhs._anonymous_rss, epsilon);
}

std::string
ProcessMemoryStats::toString() const
{
    vespalib::asciistream stream;
    stream << "_virt=" << _virt << ", "
           << "_mapped_rss=" << _mapped_rss << ", "
           << "_anonymous_rss=" << _anonymous_rss;
    return stream.str();
}

ProcessMemoryStats
ProcessMemoryStats::create(double epsilon, SamplingStrategy strategy)
{
    constexpr size_t NUM_TRIES = 3;
    std::vector<ProcessMemoryStats> samples;
    samples.reserve(NUM_TRIES + 1);
    samples.push_back(sample(strategy));
    for (size_t i = 0; i < NUM_TRIES; ++i) {
        samples.push_back(sample(strategy));
        if (samples.back().similarTo(*(samples.rbegin()+1), epsilon)) {
            return samples.back();
        }
        LOG(debug, "create(): Memory stats have changed, trying to sample again: i=%zu, prevStats={%s}, currStats={%s}",
            i, (samples.rbegin()+1)->toString().c_str(), samples.back().toString().c_str());
    }
    std::sort(samples.begin(), samples.end());
    LOG(debug, "We failed to find 2 consecutive samples that were similar with epsilon of %d%%.\nSmallest is '%s',\n median is '%s',\n largest is '%s'",
        int(epsilon*1000), samples.front().toString().c_str(), samples[samples.size()/2].toString().c_str(), samples.back().toString().c_str());
    return samples[samples.size()/2];
}

}
