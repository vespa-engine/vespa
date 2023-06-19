// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <limits>

namespace search::expression {
	
class CurrentIndex {
public:
    CurrentIndex() noexcept : _index(0) {}
    uint32_t get() const noexcept { return _index; }
    void set(int64_t index) noexcept {
        _index = (index > 0) ? index : 0;
    }
private:
    uint32_t _index;
};

}
