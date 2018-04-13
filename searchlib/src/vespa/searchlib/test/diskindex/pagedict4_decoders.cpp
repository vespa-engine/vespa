// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "pagedict4_decoders.h"

namespace search::diskindex::test {

PageDict4Decoders::PageDict4Decoders(uint32_t chunkSize, uint64_t numWordIds)
    : ssd(),
      spd(),
      pd()
{
    ssd._minChunkDocs = chunkSize;
    ssd._numWordIds = numWordIds;
    spd.copyParams(ssd);
    pd.copyParams(ssd);
}

PageDict4Decoders::~PageDict4Decoders() = default;

}
