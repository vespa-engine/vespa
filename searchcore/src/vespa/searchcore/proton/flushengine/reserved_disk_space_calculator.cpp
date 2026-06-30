// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "reserved_disk_space_calculator.h"

using searchcorespi::IFlushTarget;

namespace proton::flushengine {

ReservedDiskSpaceCalculator::ReservedDiskSpaceCalculator(size_t concurrent, uint64_t max_summary_file_size) noexcept
    : _concurrent(concurrent),
      _max_summary_file_size(max_summary_file_size),
      _candidates(),
      _high_priority_candidate(),
      _reserved_grow(0) {
}

ReservedDiskSpaceCalculator::~ReservedDiskSpaceCalculator() = default;

bool ReservedDiskSpaceCalculator::has_normal_high_priority_candidate() const noexcept {
    for (auto& candidate : _candidates) {
        if (candidate.high_priority()) {
            return true;
        }
    }
    return false;
}

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
    Candidate candidate(reserved_flush, high_priority);
    if (candidate.high_priority()) {
        if (!_high_priority_candidate.has_value() || _high_priority_candidate.value() < candidate) {
            _high_priority_candidate = candidate;
        }
    }
    _candidates.push_back(candidate);
}

uint64_t ReservedDiskSpaceCalculator::get_reserved_disk() {
    if (_concurrent < _candidates.size()) {
        /*
         * Retain the _concurrent biggest candidates. They are later used to calculate reserved
         * disk space for flush.
         */
        std::nth_element(_candidates.begin(), _candidates.begin() + _concurrent, _candidates.end());
        _candidates.resize(_concurrent);
    }
    if (_concurrent == _candidates.size() && _concurrent != 0) {
        if (!has_normal_high_priority_candidate()) {
            // One of the flush slots can only be used for high priority flush targets
            std::nth_element(_candidates.begin(), _candidates.begin() + _concurrent - 1, _candidates.end());
            _candidates.resize(_concurrent - 1);
            if (_high_priority_candidate.has_value()) {
                _candidates.push_back(_high_priority_candidate.value());
            }
        }
    }
    uint64_t reserved_flush = 0;
    for (auto& candidate : _candidates) {
        reserved_flush += candidate.reserved();
    }
    return _reserved_grow + reserved_flush;
}

} // namespace proton::flushengine
