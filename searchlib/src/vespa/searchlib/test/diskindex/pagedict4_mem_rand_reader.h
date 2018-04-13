// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "pagedict4_decoders.h"
#include "threelevelcountbuffers.h"
#include <vespa/searchlib/bitcompression/pagedict4.h>

namespace search::diskindex::test {

/*
 * Class for performing random lookups in memory based pagedict4 structure
 */
class PageDict4MemRandReader
{
public:
    using PageDict4SSReader = search::bitcompression::PageDict4SSReader;
    using PageDict4SSLookupRes = search::bitcompression::PageDict4SSLookupRes;
    using PageDict4SPLookupRes = search::bitcompression::PageDict4SPLookupRes;
    using PageDict4PLookupRes = search::bitcompression::PageDict4PLookupRes;
    using StartOffset = search::bitcompression::PageDict4StartOffset;
    using PostingListCounts = search::index::PostingListCounts;

    PageDict4Decoders _decoders;
    ThreeLevelCountReadBuffers _buffers;
    PageDict4SSReader _ssr;
    const char *_spData;
    const char *_pData;
    size_t _pageSize;

    PageDict4MemRandReader(uint32_t chunkSize, uint64_t numWordIds,
                           ThreeLevelCountWriteBuffers &wb);
    ~PageDict4MemRandReader();
    bool lookup(const std::string &key, uint64_t &wordNum,
                PostingListCounts &counts, StartOffset &offsets);
};

}
