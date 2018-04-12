// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

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
class PageDict4MemWriter : public ThreeLevelCountWriteBuffers
{
public:
    using PageDict4SSWriter = search::bitcompression::PageDict4SSWriter;
    using PageDict4SPWriter = search::bitcompression::PageDict4SPWriter;
    using PageDict4PWriter = search::bitcompression::PageDict4PWriter;
    using PostingListCounts = search::index::PostingListCounts;

    PageDict4SSWriter *_ssw;
    PageDict4SPWriter *_spw;
    PageDict4PWriter *_pw;

    PageDict4MemWriter(EC &sse, EC &spe, EC &pe);
    ~PageDict4MemWriter();
    void allocWriters();
    void flush();
    void addCounts(const std::string &word, const PostingListCounts &counts);
};

}
