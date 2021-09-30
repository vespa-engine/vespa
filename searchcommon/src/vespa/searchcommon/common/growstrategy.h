// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/growstrategy.h>
#include <cstdint>
#include <iosfwd>

namespace search {

class GrowStrategy
{
private:
    uint32_t _docsInitialCapacity;
    float    _docsGrowFactor;
    uint32_t _docsGrowDelta;
    float    _multiValueAllocGrowFactor;
public:
    GrowStrategy() noexcept
        : GrowStrategy(1024, 0.5, 0, 0.2)
    {}
    GrowStrategy(uint32_t docsInitialCapacity, float docsGrowFactor,
                 uint32_t docsGrowDelta, float multiValueAllocGrowFactor) noexcept
        : _docsInitialCapacity(docsInitialCapacity),
          _docsGrowFactor(docsGrowFactor),
          _docsGrowDelta(docsGrowDelta),
          _multiValueAllocGrowFactor(multiValueAllocGrowFactor)
    {
    }

    static GrowStrategy make(uint32_t docsInitialCapacity, float docsGrowFactor, uint32_t docsGrowDelta) {
        return GrowStrategy(docsInitialCapacity, docsGrowFactor, docsGrowDelta, 0.2);
    }

    uint32_t    getDocsInitialCapacity() const { return _docsInitialCapacity; }
    uint32_t        getDocsGrowPercent() const { return _docsGrowFactor*100; }
    float            getDocsGrowFactor() const { return _docsGrowFactor; }
    uint32_t          getDocsGrowDelta() const { return _docsGrowDelta; }
    float getMultiValueAllocGrowFactor() const { return _multiValueAllocGrowFactor; }
    void    setDocsInitialCapacity(uint32_t v) { _docsInitialCapacity = v; }
    void          setDocsGrowDelta(uint32_t v) { _docsGrowDelta = v; }

    vespalib::GrowStrategy to_generic_strategy() const {
        return vespalib::GrowStrategy(_docsInitialCapacity, _docsGrowFactor, _docsGrowDelta);
    }

    bool operator==(const GrowStrategy & rhs) const {
        return _docsInitialCapacity == rhs._docsInitialCapacity &&
            _docsGrowFactor == rhs._docsGrowFactor &&
            _docsGrowDelta == rhs._docsGrowDelta &&
            _multiValueAllocGrowFactor == rhs._multiValueAllocGrowFactor;
    }
    bool operator!=(const GrowStrategy & rhs) const {
        return !(operator==(rhs));
    }
};

std::ostream& operator<<(std::ostream& os, const GrowStrategy& grow_strategy);

}

