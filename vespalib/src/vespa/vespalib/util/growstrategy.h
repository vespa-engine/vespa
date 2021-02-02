// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>

namespace vespalib {

class GrowStrategy {
private:
    size_t _initialCapacity;
    float  _growFactor;
    size_t _growDelta;
public:
    GrowStrategy() noexcept
        : GrowStrategy(1024, 0.5, 0)
    {}
    GrowStrategy(size_t initialCapacity, float growPercent, size_t growDelta) noexcept
        : _initialCapacity(initialCapacity),
          _growFactor(growPercent),
          _growDelta(growDelta)
    {
    }

    static GrowStrategy make(size_t initialCapacity, float growFactor, size_t growDelta) noexcept {
        return GrowStrategy(initialCapacity, growFactor, growDelta);
    }

    size_t getInitialCapacity() const noexcept { return _initialCapacity; }
    size_t     getGrowPercent() const noexcept { return _growFactor*100; }
    float       getGrowFactor() const noexcept { return _growFactor; }
    size_t       getGrowDelta() const noexcept { return _growDelta; }
    void setInitialCapacity(size_t v) noexcept { _initialCapacity = v; }
    void       setGrowDelta(size_t v) noexcept { _growDelta = v; }

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

