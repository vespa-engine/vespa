// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/growstrategy.h>
#include <cstdint>
#include <iosfwd>

namespace search {

class GrowStrategy : public vespalib::GrowStrategy
{
private:
    float    _multiValueAllocGrowFactor;
public:
    GrowStrategy() noexcept
        : GrowStrategy(1024, 0.5, 0, 0, 0.2)
    {}
    GrowStrategy(uint32_t docsInitialCapacity, float docsGrowFactor,
                 uint32_t docsGrowDelta, uint32_t docsMinimumCapacity, float multiValueAllocGrowFactor) noexcept
        : vespalib::GrowStrategy(docsInitialCapacity, docsGrowFactor, docsGrowDelta, docsMinimumCapacity),
          _multiValueAllocGrowFactor(multiValueAllocGrowFactor)
    {
    }

    static GrowStrategy make(uint32_t docsInitialCapacity, float docsGrowFactor, uint32_t docsGrowDelta) {
        return {docsInitialCapacity, docsGrowFactor, docsGrowDelta, 0, 0.2};
    }

    float getMultiValueAllocGrowFactor() const { return _multiValueAllocGrowFactor; }

    bool operator==(const GrowStrategy & rhs) const {
        return vespalib::GrowStrategy::operator==(rhs) &&
                (_multiValueAllocGrowFactor == rhs._multiValueAllocGrowFactor);
    }
    bool operator!=(const GrowStrategy & rhs) const {
        return !(operator==(rhs));
    }
};

std::ostream& operator<<(std::ostream& os, const GrowStrategy& grow_strategy);

}

