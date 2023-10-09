// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <algorithm>
#include <cstddef>
#include <cstdint>

namespace vespalib {

class GrowStrategy {
private:
    uint32_t _initialCapacity;
    uint32_t _minimumCapacity;
    uint32_t _growDelta;
    float  _growFactor;
public:
    GrowStrategy() noexcept
        : GrowStrategy(1024, 0.5, 0, 0)
    {}
    GrowStrategy(uint32_t initialCapacity, float growPercent, uint32_t growDelta, uint32_t minimumCapacity) noexcept
        : _initialCapacity(initialCapacity),
          _minimumCapacity(minimumCapacity),
          _growDelta(growDelta),
          _growFactor(growPercent)
    {
    }

    size_t getMinimumCapacity() const noexcept { return _minimumCapacity; }
    size_t getInitialCapacity() const noexcept { return _initialCapacity; }
    float       getGrowFactor() const noexcept { return _growFactor; }
    size_t       getGrowDelta() const noexcept { return _growDelta; }

    void         setInitialCapacity(size_t v) noexcept { _initialCapacity = v; }
    void         setGrowDelta(size_t v) noexcept { _growDelta = v; }

    size_t calc_new_size(size_t base_size) const noexcept {
        size_t delta = (base_size * getGrowFactor()) + getGrowDelta();
        size_t new_size = base_size + std::max(delta, static_cast<size_t>(1));
        return std::max(new_size, getMinimumCapacity());
    }

    bool operator==(const GrowStrategy & rhs) const noexcept {
        return (_initialCapacity == rhs._initialCapacity &&
                _minimumCapacity == rhs._minimumCapacity &&
                _growFactor == rhs._growFactor &&
                _growDelta == rhs._growDelta);
    }
    bool operator!=(const GrowStrategy & rhs) const noexcept {
        return !(operator==(rhs));
    }
};

}

