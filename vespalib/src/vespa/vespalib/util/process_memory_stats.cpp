// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "process_memory_stats.h"

#include "exceptions.h"
#include "size_literals.h"
#include "transient_memory_tracker.h"

#include <vespa/vespalib/stllike/asciistream.h>

#include <unistd.h>

#include <algorithm>
#include <cinttypes>
#include <vector>
#if defined(__APPLE__)
#include <mach/mach.h>
#endif

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.util.process_memory_stats");

namespace vespalib {

size_t ProcessMemoryStats::normal_page_size = sysconf(_SC_PAGESIZE);

/*
 * The statm line looks like this:
 * size resident shared text lib data dt
 *
 * Example:
 * 3332000 1917762 8060 1 0 2960491 0
 *
 * The numbers specify the numbers of pages
 */
ProcessMemoryStats ProcessMemoryStats::createStatsFromStatm() {
    ProcessMemoryStats ret;
    auto               lock = TransientMemoryTracker::acquire_lock();
#ifdef __linux__
    asciistream statm = asciistream::createFromDevice("/proc/self/statm");
    ret = parseStatm(statm);
#elif defined(__APPLE__)
    task_vm_info_data_t    vm_info;
    mach_msg_type_number_t count = TASK_VM_INFO_COUNT;
    kern_return_t          result = task_info(mach_task_self(), TASK_VM_INFO, (task_info_t)&vm_info, &count);
    if (result == KERN_SUCCESS) {
        ret._virt = vm_info.virtual_size;
        ret._anonymous_rss = vm_info.phys_footprint;
    }
#endif
    ret._transient_memory_for_flush = TransientMemoryTracker::get_total_transient_memory(std::move(lock));
    return ret;
}

ProcessMemoryStats ProcessMemoryStats::parseStatm(asciistream& statm) {
    ProcessMemoryStats ret;
    try {
        // the first three values in statm are size, resident, and shared
        // the values in statm are measured in numbers of pages
        uint64_t size, resident, shared;
        statm >> size >> resident >> shared;

        // we only get the total program size via statm (no distinction between anonymous and non-anonymous)
        // VmSize (in status) = size (in statm)
        ret._virt = size * normal_page_size;

        // RssAnon (in status) = resident - shared (in statm)
        ret._anonymous_rss = (resident - shared) * normal_page_size;

        // RssFile + RssShmem (in status) = shared (in statm)
        ret._mapped_rss = shared * normal_page_size;

    } catch (const IllegalArgumentException& e) {
        LOG(warning, "Error '%s' while reading statm line '%s'", e.what(), statm.str().c_str());
    }

    return ret;
}

ProcessMemoryStats::ProcessMemoryStats() noexcept : ProcessMemoryStats(0, 0, 0, 0) {
}

ProcessMemoryStats::ProcessMemoryStats(uint64_t virt, uint64_t mapped_rss, uint64_t anonymous_rss) noexcept
    : ProcessMemoryStats(virt, mapped_rss, anonymous_rss, 0) {
}

ProcessMemoryStats::ProcessMemoryStats(uint64_t virt, uint64_t mapped_rss, uint64_t anonymous_rss,
                                       size_t transient_memory_for_flush_) noexcept
    : _virt(virt),
      _mapped_rss(mapped_rss),
      _anonymous_rss(anonymous_rss),
      _transient_memory_for_flush(transient_memory_for_flush_) {
}

namespace {

bool similar(uint64_t lhs, uint64_t rhs, double epsilon) {
    uint64_t maxDiff = std::max(uint64_t(1_Mi), uint64_t(epsilon * (lhs + rhs) / 2.0));
    return (lhs < rhs) ? ((rhs - lhs) <= maxDiff) : ((lhs - rhs) <= maxDiff);
}

} // namespace

bool ProcessMemoryStats::similarTo(const ProcessMemoryStats& rhs, double epsilon) const noexcept {
    return similar(_virt, rhs._virt, epsilon) && similar(_mapped_rss, rhs._mapped_rss, epsilon) &&
           similar(_anonymous_rss, rhs._anonymous_rss, epsilon);
}

std::string ProcessMemoryStats::toString() const {
    vespalib::asciistream stream;
    stream << "_virt=" << _virt << ", _mapped_rss=" << _mapped_rss << ", _anonymous_rss=" << _anonymous_rss
           << ", transient_memory_for_flush=" << _transient_memory_for_flush;
    return stream.str();
}

ProcessMemoryStats ProcessMemoryStats::create(double epsilon) {
    constexpr size_t                NUM_TRIES = 3;
    std::vector<ProcessMemoryStats> samples;
    samples.reserve(NUM_TRIES + 1);
    samples.push_back(createStatsFromStatm());
    for (size_t i = 0; i < NUM_TRIES; ++i) {
        samples.push_back(createStatsFromStatm());
        if (samples.back().similarTo(*(samples.rbegin() + 1), epsilon)) {
            return samples.back();
        }
        LOG(debug,
            "create(): Memory stats have changed, trying to sample again: i=%zu, prevStats={%s}, currStats={%s}", i,
            (samples.rbegin() + 1)->toString().c_str(), samples.back().toString().c_str());
    }
    std::sort(samples.begin(), samples.end());
    LOG(debug,
        "We failed to find 2 consecutive samples that were similar with epsilon of %d%%.\nSmallest is '%s',\n median "
        "is '%s',\n largest is '%s'",
        int(epsilon * 1000), samples.front().toString().c_str(), samples[samples.size() / 2].toString().c_str(),
        samples.back().toString().c_str());
    return samples[samples.size() / 2];
}

} // namespace vespalib
