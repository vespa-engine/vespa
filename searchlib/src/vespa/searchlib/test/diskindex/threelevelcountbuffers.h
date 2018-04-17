// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/bitcompression/countcompression.h>
#include <limits>

namespace search::diskindex {

class ThreeLevelCountWriteBuffers
{
public:
    typedef search::bitcompression::PostingListCountFileEncodeContext EC;
    EC &_sse;
    EC &_spe;
    EC &_pe;
    ComprFileWriteContext _wcsse;
    ComprFileWriteContext _wcspe;
    ComprFileWriteContext _wcpe;

    uint32_t _ssHeaderLen; // Length of header for sparse sparse file (bytes)
    uint32_t _spHeaderLen; // Length of header for sparse page file (bytes)
    uint32_t _pHeaderLen;  // Length of header for page file (bytes)

    uint64_t _ssFileBitSize;
    uint64_t _spFileBitSize;
    uint64_t _pFileBitSize;

    ThreeLevelCountWriteBuffers(EC &sse, EC &spe, EC &pe);
    ~ThreeLevelCountWriteBuffers();

    void flush();

    // unit test method.  Just pads without writing proper header
    void startPad(uint32_t ssHeaderLen, uint32_t spHeaderLen, uint32_t pHeaderLen);
};


class ThreeLevelCountReadBuffers
{
public:
    typedef search::bitcompression::PostingListCountFileEncodeContext EC;
    typedef search::bitcompression::PostingListCountFileDecodeContext DC;
    DC &_ssd;
    DC &_spd;
    DC &_pd;
    ComprFileReadContext _rcssd;
    ComprFileReadContext _rcspd;
    ComprFileReadContext _rcpd;

    uint32_t _ssHeaderLen;
    uint32_t _spHeaderLen;
    uint32_t _pHeaderLen;

    uint64_t _ssFileBitSize;
    uint64_t _spFileBitSize;
    uint64_t _pFileBitSize;

    // Unit test usage constructor.
    ThreeLevelCountReadBuffers(DC &ssd, DC &spd, DC &pd, ThreeLevelCountWriteBuffers &wb);

    // Normal usage constructor
    ThreeLevelCountReadBuffers(DC &ssd, DC &spd, DC &pd);
    ~ThreeLevelCountReadBuffers();
};

}
