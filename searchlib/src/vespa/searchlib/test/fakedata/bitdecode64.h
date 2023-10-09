// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bitencode64.h"
#include <vespa/searchlib/util/comprfile.h>
#include <vespa/searchlib/bitcompression/compression.h>

namespace search::fakedata {

template <bool bigEndian>
class BitDecode64 : public bitcompression::DecodeContext64<bigEndian>
{
private:
    const uint64_t *_comprBase;
    int _bitOffsetBase;
    using ParentClass = bitcompression::DecodeContext64<bigEndian>;

public:
    using ParentClass::_val;
    using ParentClass::_valI;
    using ParentClass::_preRead;
    using ParentClass::_cacheInt;
    using EC = typename bitcompression::DecodeContext64<bigEndian>::EC;

    BitDecode64(const uint64_t *compr, int bitOffset)
        : bitcompression::DecodeContext64<bigEndian>(compr, bitOffset),
          _comprBase(compr),
          _bitOffsetBase(bitOffset)
    {
    }

    using DC = bitcompression::DecodeContext64<bigEndian>;

    void seek(uint64_t offset) {
        offset += _bitOffsetBase;
        const uint64_t *compr = _comprBase + (offset / 64);
        int bitOffset = offset & 63;
        _valI = compr + 1;
        _val = 0;
        _cacheInt = EC::bswap(*compr);
        _preRead = 64 - bitOffset;
        uint32_t length = 64;
        UC64_READBITS(_val, _valI, _preRead, _cacheInt, EC);
    }

    uint64_t getOffset() const {
        return 64 * (_valI - _comprBase - 1) - this->_preRead - _bitOffsetBase;
    }

    uint64_t getOffset(const uint64_t *valI, int preRead) const {
        return 64 * (valI - _comprBase - 1) - preRead - _bitOffsetBase;
    }

    const uint64_t * getComprBase() const { return _comprBase; }
    int getBitOffsetBase() const { return _bitOffsetBase; }
};


extern template class BitDecode64<true>;
extern template class BitDecode64<false>;

using BitDecode64BE = BitDecode64<true>;

}
