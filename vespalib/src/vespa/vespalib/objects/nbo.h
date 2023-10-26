// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <arpa/inet.h>

namespace vespalib {
class nbo {
public:
    static bool     n2h(bool v)     { return v; }
    static int8_t   n2h(int8_t v)   { return v; }
    static uint8_t  n2h(uint8_t v)  { return v; }
    static char     n2h(char v)     { return v; }
    static int16_t  n2h(int16_t v)  { return ntohs(v); }
    static uint16_t n2h(uint16_t v) { return ntohs(v); }
    static int32_t  n2h(int32_t v)  { return ntohl(v); }
    static uint32_t n2h(uint32_t v) { return ntohl(v); }
    static int64_t  n2h(int64_t v)  { return nbo_ntohll(v); }
    static uint64_t n2h(uint64_t v) { return nbo_ntohll(v); }
    static float    n2h(float v)    {
        union { uint32_t _u; float _f; } uf;
        uf._f = v;
        uf._u = ntohl(uf._u);
        return uf._f;
    }
    static double   n2h(double v)   {
        union { uint64_t _u; double _f; } uf;
        uf._f = v;
        uf._u = nbo_ntohll(uf._u);
        return uf._f;
    }
private:
    static uint64_t nbo_ntohll(uint64_t v) {
        union { uint64_t _ll; uint32_t _l[2]; } w, r;
        r._ll = v;
        w._l[0] = n2h(r._l[1]);
        w._l[1] = n2h(r._l[0]);
        return w._ll;
    }
};

}
