// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "pagedict4_encoders.h"
#include "threelevelcountbuffers.h"

namespace search::bitcompression {

class PageDict4SSWriter;
class PageDict4SPWriter;
class PageDict4PWriter;

}

namespace search::index { class PostingListCounts; }

namespace search::diskindex::test {

/*
 * Class for writing to memory based pagedict4 structure
 */
class PageDict4MemWriter
{
public:
    using PageDict4SSWriter = search::bitcompression::PageDict4SSWriter;
    using PageDict4SPWriter = search::bitcompression::PageDict4SPWriter;
    using PageDict4PWriter = search::bitcompression::PageDict4PWriter;
    using PostingListCounts = search::index::PostingListCounts;

private:
    PageDict4Encoders _encoders;
public:
    ThreeLevelCountWriteBuffers _buffers;
private:
    PageDict4SSWriter *_ssw;
    PageDict4SPWriter *_spw;
    PageDict4PWriter *_pw;

    void allocWriters();
public:
    PageDict4MemWriter(uint32_t chunkSize, uint64_t numWordIds, uint32_t ssPad, uint32_t spPad, uint32_t pPad);
    ~PageDict4MemWriter();
    void flush();
    void addCounts(const std::string &word, const PostingListCounts &counts);
    void startPad(uint32_t ssHeaderLen, uint32_t spHeaderLen, uint32_t pHeaderLen)
    {
        _buffers.startPad(ssHeaderLen, spHeaderLen, pHeaderLen);
    }
};

}
