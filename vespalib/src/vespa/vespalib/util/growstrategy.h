// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace vespalib {

class GrowStrategy {
private:
    uint32_t _initialCapacity;
    float    _growFactor;
    uint32_t _growDelta;
public:
    GrowStrategy() noexcept
        : GrowStrategy(1024, 0.5, 0)
    {}
    GrowStrategy(uint32_t initialCapacity, float growPercent, uint32_t growDelta) noexcept
        : _initialCapacity(initialCapacity),
          _growFactor(growPercent),
          _growDelta(growDelta)
    {
    }

    static GrowStrategy make(uint32_t initialCapacity, float growFactor, uint32_t growDelta) noexcept {
        return GrowStrategy(initialCapacity, growFactor, growDelta);
    }

    uint32_t    getInitialCapacity() const noexcept { return _initialCapacity; }
    uint32_t        getGrowPercent() const noexcept { return _growFactor*100; }
    float            getGrowFactor() const noexcept { return _growFactor; }
    uint32_t          getGrowDelta() const noexcept { return _growDelta; }
    void    setInitialCapacity(uint32_t v) noexcept { _initialCapacity = v; }
    void          setGrowDelta(uint32_t v) noexcept { _growDelta = v; }

    bool operator==(const GrowStrategy & rhs) const noexcept {
        return (_initialCapacity == rhs._initialCapacity &&
                _growFactor == rhs._growFactor &&
                _growDelta == rhs._growDelta);
    }
    bool operator!=(const GrowStrategy & rhs) const noexcept {
        return !(operator==(rhs));
    }
};

}

