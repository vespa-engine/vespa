// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <stdint.h>

namespace search {

/*
 * Class describing compaction strategy for a compactable data structure.
 */
class CompactionStrategy
{
private:
    double _maxDeadRatio; // Max ratio of dead bytes before compaction
public:
    CompactionStrategy()
        : _maxDeadRatio(0.2)
    {
    }
    CompactionStrategy(double maxDeadRatio)
        : _maxDeadRatio(maxDeadRatio)
    {
    }
    double getMaxDeadRatio() const { return _maxDeadRatio; }
    bool operator==(const CompactionStrategy & rhs) const {
        return _maxDeadRatio == rhs._maxDeadRatio;
    }
    bool operator!=(const CompactionStrategy & rhs) const { return !(operator==(rhs)); }
};

} // namespace search
