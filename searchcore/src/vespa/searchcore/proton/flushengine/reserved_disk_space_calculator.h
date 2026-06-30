// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcorespi/flush/iflushtarget.h>

#include <optional>

namespace proton::flushengine {

class ReservedDiskSpaceCalculator {
    /*
     * Candidate for tracking reserved disk space for flush, used to calculate worst case need for reserved disk
     * space. The number of total flush threads determinates how many candidates to use.
     */
    class Candidate {
        uint64_t _reserved;
        bool     _high_priority;

    public:
        Candidate(uint64_t reserved_in, bool high_priority_in) noexcept
            : _reserved(reserved_in), _high_priority(high_priority_in) {}
        Candidate() noexcept : Candidate(0, false) {}
        bool operator<(const Candidate& rhs) const noexcept { return _reserved > rhs._reserved; }
        [[nodiscard]] uint64_t reserved() const noexcept { return _reserved; }
        [[nodiscard]] bool high_priority() const noexcept { return _high_priority; }
    };

    size_t                   _concurrent;
    uint64_t                 _max_summary_file_size;
    std::vector<Candidate>   _candidates; // Used to calculate worst case for concurrent flushes
    std::optional<Candidate> _high_priority_candidate;
    uint64_t                 _reserved_grow;

    bool has_normal_high_priority_candidate() const noexcept;

public:
    using IFlushTarget = searchcorespi::IFlushTarget;
    ReservedDiskSpaceCalculator(size_t concurrent, uint64_t max_summary_file_size) noexcept;
    ~ReservedDiskSpaceCalculator();
    void track_disk_gain(const IFlushTarget::DiskGain& gain, IFlushTarget::Type type,
                         IFlushTarget::Component component, bool high_priority);
    uint64_t get_reserved_disk();
};

} // namespace proton::flushengine
