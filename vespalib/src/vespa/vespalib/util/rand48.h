// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdlib>
#include <cstdint>

namespace vespalib {

/*
 * Simple random generator based on lrand48() spec.
 */
class Rand48
{
private:
    uint64_t _state;
public:
    void srand48(long seed) {
        _state = ((static_cast<uint64_t>(seed & 0xffffffffu)) << 16) + 0x330e;
    }

    Rand48()
        : Rand48(0x1234abcd)
    { }
    explicit Rand48(long seed)
        : _state(0)
    {
        srand48(seed);
    }
    void iterate() {
        _state = (UINT64_C(0x5DEECE66D) * _state + 0xb) &
                 UINT64_C(0xFFFFFFFFFFFF);
    }
    /*
     * Return value from 0 to 2^31 - 1
     */
    long lrand48() {
        iterate();
        return static_cast<long>(_state >> 17);
    }
};

}
