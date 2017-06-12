// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "zstdcompressor.h"
#include <vespa/vespalib/util/alloc.h>
#include <zstd.h>
#include <cassert>

using vespalib::alloc::Alloc;

namespace document {

size_t ZStdCompressor::adjustProcessLen(uint16_t, size_t len)   const { return ZSTD_compressBound(len); }
size_t ZStdCompressor::adjustUnProcessLen(uint16_t, size_t len) const { return len; }

bool
ZStdCompressor::process(const CompressionConfig& config, const void * inputV, size_t inputLen, void * outputV, size_t & outputLenV)
{
    size_t maxOutputLen = ZSTD_compressBound(inputLen);
    size_t sz = ZSTD_compress(outputV, maxOutputLen, inputV, inputLen, config.compressionLevel);
    assert( ! ZSTD_isError(sz) );
    outputLenV = sz;
    return ! ZSTD_isError(sz);
    
}

bool
ZStdCompressor::unprocess(const void * inputV, size_t inputLen, void * outputV, size_t & outputLenV)
{
    size_t sz = ZSTD_decompress(outputV, outputLenV, inputV, inputLen);
    assert( ! ZSTD_isError(sz) );
    outputLenV = sz;
    return ! ZSTD_isError(sz);
}

}
