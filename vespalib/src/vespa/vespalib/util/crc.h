// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/bits.h>

namespace vespalib {

/**
 * @brief Crc32 class
 *
 * This has fast Crc32 calculation base on table lookup.
 **/
class crc_32_type
{
public:
    crc_32_type() : _c(uint32_t(-1)) { }
    void process_bytes(const void *start, size_t sz);
    uint32_t checksum() const { return _c ^ uint32_t(-1); }
    static uint32_t crc(const void * v, size_t sz);
private:
    uint32_t _c;
    class CrcTableInit
    {
    public:
        CrcTableInit();
        static uint32_t crc(uint8_t v);
    };
    static uint32_t _crc[256];
    static CrcTableInit _crcTableInit;
};

}

