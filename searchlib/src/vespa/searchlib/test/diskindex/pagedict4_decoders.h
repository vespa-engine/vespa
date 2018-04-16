// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/bitcompression/countcompression.h>

namespace search::diskindex::test {

/*
 * Class for writing to memory based pagedict4 structure
 */
struct PageDict4Decoders
{
    using DC = search::bitcompression::PostingListCountFileDecodeContext;

    DC ssd;
    DC spd;
    DC pd;

    PageDict4Decoders(uint32_t chunkSize, uint64_t numWordIds);
    ~PageDict4Decoders();
};

}
