// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcorespi/flush/iflushtarget.h>

namespace proton::flushengine {

class ReservedDiskSpaceCalculator {
    /*
     * Candidate for tracking reserved disk space for flush, used to calculate worst case need for reserved disk
     * space. The number of total flush threads determinates how many candidates to use.
     */
    class Candidate {
        uint64_t _reserved;

    public:
        explicit Candidate(uint64_t reserved_in) noexcept
            : _reserved(reserved_in)
        {
        }
        Candidate() noexcept
            : Candidate(0)
        {
        }
        bool operator<(const Candidate& rhs) const noexcept {
            return _reserved > rhs._reserved;
        }
        uint64_t reserved() const noexcept { return _reserved; }
    };

    size_t _concurrent;
    std::vector<Candidate> _candidates; // Used to calculate worst case for concurrent flushes
    uint64_t _reserved_grow;

public:
    explicit ReservedDiskSpaceCalculator(size_t concurrent) noexcept;
    ~ReservedDiskSpaceCalculator();
    void track_disk_gain(const searchcorespi::IFlushTarget::DiskGain& gain);
    uint64_t get_reserved_disk();
};

}
