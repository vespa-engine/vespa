// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/util/crc.h>

namespace vespalib {

uint32_t            crc_32_type::_crc[256];
crc_32_type::CrcTableInit crc_32_type::_crcTableInit;

uint32_t crc_32_type::crc(const void * src, size_t sz)
{
    const uint8_t *v = static_cast<const uint8_t *>(src);
    uint32_t c(uint32_t(-1));

    for (size_t i(0); i < sz; i++ ) {
        c = (c >> 8) ^ _crc[ uint8_t(c ^ v[i]) ];
    }

    return c ^ (uint32_t(-1));
}

void crc_32_type::process_bytes(const void *start, size_t sz)
{
    const uint8_t *v = static_cast<const uint8_t *>(start);
    uint32_t c(_c);

    for (size_t i(0); i < sz; i++ ) {
        c = (c >> 8) ^ _crc[ uint8_t(c ^ v[i]) ];
    }
    _c = c;
}

crc_32_type::CrcTableInit::CrcTableInit()
{
    Bits::forceInitNow();
    uint8_t  dividend = 0;
    do {
        _crc[Bits::reverse(dividend)] = crc(dividend);
    } while ( ++dividend );
}

uint32_t crc_32_type::CrcTableInit::crc(uint8_t dividend)
{
    const uint32_t fast_hi_bit = 1ul << 31;
    const uint8_t  byte_hi_bit = 1u << 7;
    uint32_t remainder = 0;

    // go through all the dividend's bits
    for ( uint8_t mask = byte_hi_bit ; mask ; mask >>= 1 ) {
        // check if divisor fits
        if ( dividend & mask ) {
            remainder ^= fast_hi_bit;
        }

        // do polynominal division
        if ( remainder & fast_hi_bit ) {
            remainder <<= 1;
            remainder ^= 0x04C11DB7;
        } else {
            remainder <<= 1;
        }
    }
    return Bits::reverse( remainder );
}

}
