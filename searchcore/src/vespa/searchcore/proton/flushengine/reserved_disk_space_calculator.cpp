// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "reserved_disk_space_calculator.h"

using searchcorespi::IFlushTarget;

namespace proton::flushengine {

ReservedDiskSpaceCalculator::ReservedDiskSpaceCalculator(size_t concurrent, uint64_t max_summary_file_size) noexcept
    : _max_summary_file_size(max_summary_file_size), _reserved_grow(0), _candidates(concurrent) {
}

ReservedDiskSpaceCalculator::~ReservedDiskSpaceCalculator() = default;

void ReservedDiskSpaceCalculator::track_disk_gain(const IFlushTarget::DiskGain& gain, IFlushTarget::Type type,
                                                  IFlushTarget::Component component, bool high_priority) {
    if (gain.getAfter() > gain.getBefore()) {
        _reserved_grow += gain.getAfter() - gain.getBefore();
    }
    uint64_t reserved_flush = gain.getAfter();
    if (type == IFlushTarget::Type::GC && component == IFlushTarget::Component::DOCUMENT_STORE &&
        reserved_flush > _max_summary_file_size)
    {
        // Flush targets for document store compaction only compacts a single file at a time.
        reserved_flush = _max_summary_file_size;
    }
    _candidates.add_candidate(reserved_flush, high_priority);
}

} // namespace proton::flushengine
