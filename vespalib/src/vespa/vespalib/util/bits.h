// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>
#include <sys/types.h>

namespace vespalib {

/**
 * @brief Bit fiddling class
 *
 * This class handles low level bit manipulation operations.
 * - Fast bit reversal by table lookup.
 **/
class Bits
{
public:
    static uint8_t  reverse(uint8_t v) { return _reverse[v]; }
    static uint16_t reverse(uint16_t v) {
        union { uint16_t v; uint8_t a[2]; } s, d;
        s.v = v;
        d.a[0] = _reverse[s.a[1]];
        d.a[1] = _reverse[s.a[0]];
        return d.v;
    }
    static uint32_t reverse(uint32_t v) {
        union { uint32_t v; uint8_t a[4]; } s, d;
        s.v = v;
        d.a[0] = _reverse[s.a[3]];
        d.a[1] = _reverse[s.a[2]];
        d.a[2] = _reverse[s.a[1]];
        d.a[3] = _reverse[s.a[0]];
        return d.v;
    }
    static uint64_t reverse(uint64_t v) {
        union { uint64_t v; uint8_t a[8]; } s, d;
        s.v = v;
        d.a[0] = _reverse[s.a[7]];
        d.a[1] = _reverse[s.a[6]];
        d.a[2] = _reverse[s.a[5]];
        d.a[3] = _reverse[s.a[4]];
        d.a[4] = _reverse[s.a[3]];
        d.a[5] = _reverse[s.a[2]];
        d.a[6] = _reverse[s.a[1]];
        d.a[7] = _reverse[s.a[0]];
        return d.v;
    }
    static void * reverse(void * v, size_t sz);
    /**
      Utility for other statically contructed objects to force correct init order."
    **/
    static void forceInitNow();
private:
    class ReverseTableInit
    {
    public:
        ReverseTableInit();
        static uint8_t reverse(uint8_t v);
    };
    static uint8_t _reverse[256];
    static ReverseTableInit _reverseTableInit;
};

}


