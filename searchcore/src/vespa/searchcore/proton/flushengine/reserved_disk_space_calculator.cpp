// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "reserved_disk_space_calculator.h"

using searchcorespi::IFlushTarget;

namespace proton::flushengine {

ReservedDiskSpaceCalculator::ReservedDiskSpaceCalculator(size_t concurrent) noexcept
    : _concurrent(concurrent),
      _candidates(),
      _reserved_grow(0)
{
}

ReservedDiskSpaceCalculator::~ReservedDiskSpaceCalculator() = default;

void
ReservedDiskSpaceCalculator::track_disk_gain(const IFlushTarget::DiskGain &gain)
{
    if (gain.getAfter() > gain.getBefore()) {
        _reserved_grow += gain.getAfter() - gain.getBefore();
    }
    _candidates.emplace_back(gain.getAfter());
}

uint64_t
ReservedDiskSpaceCalculator::get_reserved_disk()
{
    uint64_t reserved = _reserved_grow;
    if (_concurrent < _candidates.size()) {
        std::nth_element(_candidates.begin(), _candidates.begin() + _concurrent, _candidates.end());
        _candidates.resize(_concurrent);
    }
    for (auto &candidate : _candidates) {
        reserved += candidate.reserved();
    }
    return reserved;
}

}
