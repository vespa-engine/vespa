// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "reserved_resource_candidates.h"

#include <vespa/searchcorespi/flush/iflushtarget.h>

namespace proton::flushengine {

class ReservedDiskSpaceCalculator {

    uint64_t                             _max_summary_file_size;
    uint64_t                             _reserved_grow;
    ReservedResourceCandidates<uint64_t> _candidates;

public:
    using IFlushTarget = searchcorespi::IFlushTarget;
    ReservedDiskSpaceCalculator(size_t concurrent, uint64_t max_summary_file_size) noexcept;
    ~ReservedDiskSpaceCalculator();
    void track_disk_gain(const IFlushTarget::DiskGain& gain, IFlushTarget::Type type,
                         IFlushTarget::Component component, bool high_priority);
    [[nodiscard]] uint64_t reserved_disk_space_for_flush() { return _candidates.reserved_resource_for_flush(); }
    [[nodiscard]] uint64_t reserved_disk_space_for_growth() const noexcept { return _reserved_grow; };
};

} // namespace proton::flushengine
