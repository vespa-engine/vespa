// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <iosfwd>

namespace search {

/*
 * Class describing compaction strategy for a compactable data structure.
 */
class CompactionStrategy
{
private:
    double _maxDeadBytesRatio; // Max ratio of dead bytes before compaction
    double _maxDeadAddressSpaceRatio; // Max ratio of dead address space before compaction
public:
    CompactionStrategy() noexcept
        : _maxDeadBytesRatio(0.2),
          _maxDeadAddressSpaceRatio(0.2)
    {
    }
    CompactionStrategy(double maxDeadBytesRatio, double maxDeadAddressSpaceRatio) noexcept
        : _maxDeadBytesRatio(maxDeadBytesRatio),
          _maxDeadAddressSpaceRatio(maxDeadAddressSpaceRatio)
    {
    }
    double getMaxDeadBytesRatio() const { return _maxDeadBytesRatio; }
    double getMaxDeadAddressSpaceRatio() const { return _maxDeadAddressSpaceRatio; }
    bool operator==(const CompactionStrategy & rhs) const {
        return _maxDeadBytesRatio == rhs._maxDeadBytesRatio &&
            _maxDeadAddressSpaceRatio == rhs._maxDeadAddressSpaceRatio;
    }
    bool operator!=(const CompactionStrategy & rhs) const { return !(operator==(rhs)); }
};

std::ostream& operator<<(std::ostream& os, const CompactionStrategy& compaction_strategy);

} // namespace search
