// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "threelevelcountbuffers.h"
#include <vespa/searchlib/bitcompression/pagedict4.h>

namespace search::diskindex::test {

/*
 * Class for performing sequential reads in memory based pagedict4 structure
 */
class PageDict4MemSeqReader : public ThreeLevelCountReadBuffers
{
public:
    using PageDict4SSReader = search::bitcompression::PageDict4SSReader;
    using PageDict4Reader = search::bitcompression::PageDict4Reader;
    using PostingListCounts = search::index::PostingListCounts;
    PageDict4SSReader _ssr;
    PageDict4Reader _pr;

    PageDict4MemSeqReader(DC &ssd,
                          DC &spd,
                          DC &pd,
                          ThreeLevelCountWriteBuffers &wb);
    ~PageDict4MemSeqReader();
    void readCounts(vespalib::string &word,
                    uint64_t &wordNum,
                    PostingListCounts &counts);
};

}
