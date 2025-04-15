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
        // the first three values in statm are size, resident, and shared
        // the values in statm are measured in numbers of pages
        uint64_t size, resident, shared;
        statm >> size >> resident >> shared;

        // we only get the total program size via statm (no distinction between anonymous and non-anonymous)
        // VmSize (in status) = size (in statm)
        ret._virt = size * PAGE_SIZE;

        // RssAnon (in status) = resident - shared (in statm)
        ret._anonymous_rss = (resident - shared) * PAGE_SIZE;

        // RssFile + RssShmem (in status) = shared (in statm)
        ret._mapped_rss = shared * PAGE_SIZE;

    } catch (const IllegalArgumentException& e) {
        LOG(warning, "Error '%s' while reading statm line '%s'", e.what(), statm.c_str());
    }

    return ret;
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
ProcessMemoryStats::create(double epsilon)
{
    constexpr size_t NUM_TRIES = 3;
    std::vector<ProcessMemoryStats> samples;
    samples.reserve(NUM_TRIES + 1);
    samples.push_back(createStatsFromStatm());
    for (size_t i = 0; i < NUM_TRIES; ++i) {
        samples.push_back(createStatsFromStatm());
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
