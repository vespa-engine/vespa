// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace vespalib {

class Stride {
public:
    Stride(uint32_t distance, uint32_t steps)
        : _base(distance / steps), _extra(distance % steps), _steps(steps), _error(0) {}
    uint32_t next() {
        uint32_t step = _base;
        _error += _extra;
        if (_error >= _steps) {
            _error -= _steps;
            ++step;
        }
        return step;
    }

private:
    uint32_t _base;
    uint32_t _extra;
    uint32_t _steps;
    uint32_t _error;
};

} // namespace vespalib
