// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "compressed_read_buffer.h"
#include "compressed_write_buffer.h"

namespace search::diskindex {

/*
 * This class contains memory buffers for a disk index dictionary. It is used with the related
 * ThreeLevelCountReadBuffers by unit tests to verify that encode + decode roundtrip generates
 * original values, and by PageDictMemRandReader to verify that lookup works.
 */
class ThreeLevelCountWriteBuffers
{
public:
    using EC = search::bitcompression::FeatureEncodeContext<true>;
    using WriteBuffer = test::CompressedWriteBuffer<true>;
    WriteBuffer _ss; // sparse sparse buffer
    WriteBuffer _sp; // sparse page buffer
    WriteBuffer _p;  // page buffer
    ThreeLevelCountWriteBuffers(EC &sse, EC &spe, EC &pe);
    ~ThreeLevelCountWriteBuffers();

    void flush();

    // unit test method.  Just pads without writing proper header
    void startPad(uint32_t ssHeaderLen, uint32_t spHeaderLen, uint32_t pHeaderLen);
};

/*
 * This class contains a view of compressed data owned by the related ThreeLevelCountWriteBuffers class.
 * It is used to test that encode + decode round trip reconstructs original values.
 */
class ThreeLevelCountReadBuffers
{
public:
    using DC = search::bitcompression::FeatureDecodeContext<true>;
    using ReadBuffer = test::CompressedReadBuffer<true>;
    ReadBuffer _ss;
    ReadBuffer _sp;
    ReadBuffer _p;

    // Unit test usage constructor.
    ThreeLevelCountReadBuffers(DC &ssd, DC &spd, DC &pd, const ThreeLevelCountWriteBuffers &wb);
    ~ThreeLevelCountReadBuffers();
};

}
