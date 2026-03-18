// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <array>
#include <cstdint>
#include <initializer_list>
#include <vector>

namespace vespalib {

/**
 * A very simple performance counter set used to manually measure events across
 * a period of time.
 *
 * Not intended for whole-process or "fancy" sampling; use `perf` for that.
 *
 * For multiple sampling runs, prefer creating the PerfCounters instance up front
 * and reusing it across runs, since each counter uses OS (and often HW) resources.
 *
 * Only works on Linux, and the majority of interesting events will in practice
 * only work on non-virtualized hosts due to the many restrictions placed on
 * low-level hardware performance counter sampling in virtualized environments.
 *
 * Inspired by (but rather less sophisticated than) Highway's PerfCounters impl.
 * Ideally we'd just use that one, but it's not currently exposed as part of
 * the installed Highway headers...
 */
class PerfCounters {
public:
    enum class Event {
        // Software-level events will usually work even when virtualized:
        SW_CPU_CLOCK,         // High-resolution CPU clock
        SW_PAGE_FAULTS,       // Number of process page faults
        // Hardware-level events will likely not work when virtualized:
        HW_CYCLE_COUNT,       // Total cycles. Frequency scaling invariant if supported by the kernel/HW
        HW_INSTRUCTION_COUNT, // Retired instructions
        HW_CACHE_REFERENCES,  // Cache reference count, up to and including LLC
        HW_CACHE_MISSES,      // LLC misses
    };
    static constexpr size_t EventCount = 6; // Must (unsurprisingly) be kept in sync with Event enum
private:
    struct EventSampler {
        Event    _event;
        int      _fd;
        uint64_t _sampled_value;
    };

    std::vector<EventSampler>   _events;
    std::array<int, EventCount> _event_to_idx;
    int                         _group_fd;
public:
    explicit PerfCounters(std::initializer_list<Event> events);
    ~PerfCounters();

    // Returns whether performance event sampling is supported on the current platform.
    // May depend on both OS type and kernel settings. Returning true here does not imply
    // that all Event types are supported for sampling.
    [[nodiscard]] static bool is_supported() noexcept;

    // Returns whether there is at least one valid perf counter in the set
    [[nodiscard]] bool any_valid() const noexcept {
        // Do we have at least one valid counter?
        return _group_fd != -1;
    }

    // Returns whether this particular event is being tracked
    [[nodiscard]] bool valid(const Event ev) const noexcept {
        const int idx = _event_to_idx[static_cast<size_t>(ev)];
        if (idx < 0) {
            return false;
        }
        return _events[idx]._fd != -1;
    }

    // Start tracking the given events
    void start() noexcept;
    // Stop tracking the given events, and populate their counters.
    // get() will return the sampled event count.
    void stop() noexcept;

    // Last value sampled at stop()-time.
    // Will return zero if valid() == false or if the requested event does
    // not have an associated performance counter for this instance.
    [[nodiscard]] uint64_t get(const Event ev) const noexcept {
        const int idx = _event_to_idx[static_cast<size_t>(ev)];
        if (idx < 0) {
            return 0;
        }
        return _events[idx]._sampled_value;
    }
};

} // namespace vespalib
