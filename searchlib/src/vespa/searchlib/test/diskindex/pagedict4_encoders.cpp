// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "pagedict4_encoders.h"

namespace search::diskindex::test {

PageDict4Encoders::PageDict4Encoders(uint32_t chunkSize, uint64_t numWordIds)
    : sse(),
      spe(),
      pe()
{
    sse._minChunkDocs = chunkSize;
    sse._numWordIds = numWordIds;
    spe.copyParams(sse);
    pe.copyParams(sse);
}

PageDict4Encoders::~PageDict4Encoders() = default;

}
