// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <stdint.h>

namespace search {

class GrowStrategy
{
private:
    uint32_t _docsInitialCapacity;
    uint32_t _docsGrowPercent;
    uint32_t _docsGrowDelta;
    float    _multiValueAllocGrowFactor;
public:
    GrowStrategy()
        : GrowStrategy(1024, 50, 0, 0.2)
    {}
    GrowStrategy(uint32_t docsInitialCapacity,
                 uint32_t docsGrowPercent,
                 uint32_t docsGrowDelta,
                 float multiValueAllocGrowFactor)
        : _docsInitialCapacity(docsInitialCapacity),
          _docsGrowPercent(docsGrowPercent),
          _docsGrowDelta(docsGrowDelta),
          _multiValueAllocGrowFactor(multiValueAllocGrowFactor)
    {
    }

    static GrowStrategy make(uint32_t docsInitialCapacity,
                             uint32_t docsGrowPercent,
                             uint32_t docsGrowDelta) {
        return GrowStrategy(docsInitialCapacity, docsGrowPercent, docsGrowDelta, 0.2);
    }

    uint32_t    getDocsInitialCapacity() const { return _docsInitialCapacity; }
    uint32_t        getDocsGrowPercent() const { return _docsGrowPercent; }
    uint32_t          getDocsGrowDelta() const { return _docsGrowDelta; }
    float getMultiValueAllocGrowFactor() const { return _multiValueAllocGrowFactor; }
    void    setDocsInitialCapacity(uint32_t v) { _docsInitialCapacity = v; }
    void          setDocsGrowDelta(uint32_t v) { _docsGrowDelta = v; }

    bool operator==(const GrowStrategy & rhs) const {
        return _docsInitialCapacity == rhs._docsInitialCapacity &&
            _docsGrowPercent == rhs._docsGrowPercent &&
            _docsGrowDelta == rhs._docsGrowDelta &&
            _multiValueAllocGrowFactor == rhs._multiValueAllocGrowFactor;
    }
    bool operator!=(const GrowStrategy & rhs) const {
        return !(operator==(rhs));
    }
};

}

