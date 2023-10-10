// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib::eval {

inline double extract_bit(double a, double b) {
    int8_t value = (int8_t) a;
    uint32_t n = (uint32_t) b;
    return ((n < 8) && bool(value & (1 << n))) ? 1.0 : 0.0;
}

}
