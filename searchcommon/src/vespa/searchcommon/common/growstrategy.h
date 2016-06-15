// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <stdint.h>

namespace search {

class GrowStrategy
{
private:
    uint32_t _docsInitialCapacity;
    uint32_t _docsGrowPercent;
    uint32_t _docsGrowDelta;
public:
    GrowStrategy(uint32_t docsInitialCapacity = 1024,
                 uint32_t docsGrowPercent = 50,
                 uint32_t docsGrowDelta = 0)
        : _docsInitialCapacity(docsInitialCapacity),
          _docsGrowPercent(docsGrowPercent),
          _docsGrowDelta(docsGrowDelta)
    {
    }

    uint32_t getDocsInitialCapacity() const { return _docsInitialCapacity; }
    uint32_t     getDocsGrowPercent() const { return _docsGrowPercent; }
    uint32_t       getDocsGrowDelta() const { return _docsGrowDelta; }
    void setDocsInitialCapacity(uint32_t v) { _docsInitialCapacity = v; }
    void     setDocsGrowPercent(uint32_t v) { _docsGrowPercent = v; }
    void       setDocsGrowDelta(uint32_t v) { _docsGrowDelta = v; }

    bool operator==(const GrowStrategy & rhs) const {
        return _docsInitialCapacity == rhs._docsInitialCapacity &&
            _docsGrowPercent == rhs._docsGrowPercent &&
            _docsGrowDelta == rhs._docsGrowDelta;
    }
    bool operator!=(const GrowStrategy & rhs) const {
        return !(operator==(rhs));
    }
};

}

