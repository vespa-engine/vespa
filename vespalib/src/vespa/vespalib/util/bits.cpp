// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/util/bits.h>

namespace vespalib {

uint8_t                Bits::_reverse[256];
Bits::ReverseTableInit Bits::_reverseTableInit;

void * Bits::reverse(void * srcDst, size_t sz)
{
    uint8_t *v(static_cast<uint8_t *>(srcDst));
    size_t i(0);
    for(; i < sz/2; i++) {
        v[i] = reverse(v[sz-1-i]);
    }
    return v;
}

void Bits::forceInitNow()
{
    ReverseTableInit now;
}

Bits::ReverseTableInit::ReverseTableInit()
{
    if (_reverse[128] == 0) {
        for (size_t i(0); i < 256; i++) {
            _reverse[i] = reverse(i);
        }
    }
}

uint8_t Bits::ReverseTableInit::reverse(uint8_t v)
{
    return ((v >> 7) & 0x01) |
           ((v >> 5) & 0x02) |
           ((v >> 3) & 0x04) |
           ((v >> 1) & 0x08) |
           ((v << 1) & 0x10) |
           ((v << 3) & 0x20) |
           ((v << 5) & 0x40) |
           ((v << 7) & 0x80);
}

}
