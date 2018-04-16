// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "pagedict4_decoders.h"
#include "threelevelcountbuffers.h"
#include <vespa/searchlib/bitcompression/pagedict4.h>

namespace search::diskindex::test {

/*
 * Class for performing sequential reads in memory based pagedict4 structure
 */
class PageDict4MemSeqReader
{
public:
    using PageDict4SSReader = search::bitcompression::PageDict4SSReader;
    using PageDict4Reader = search::bitcompression::PageDict4Reader;
    using PostingListCounts = search::index::PostingListCounts;

    PageDict4Decoders _decoders;
    ThreeLevelCountReadBuffers _buffers;
    PageDict4SSReader _ssr;
    PageDict4Reader _pr;

    PageDict4MemSeqReader(uint32_t chunkSize, uint64_t numWordIds,
                          ThreeLevelCountWriteBuffers &wb);
    ~PageDict4MemSeqReader();
    void readCounts(vespalib::string &word,
                    uint64_t &wordNum,
                    PostingListCounts &counts);
};

}
