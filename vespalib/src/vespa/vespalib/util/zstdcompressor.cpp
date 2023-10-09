// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "zstdcompressor.h"
#include <vespa/vespalib/util/alloc.h>
#include <zstd.h>
#include <cassert>

using vespalib::alloc::Alloc;

namespace vespalib::compression {

namespace {

class CompressContext {
public:
    CompressContext() : _ctx(ZSTD_createCCtx()) {}
    ~CompressContext() { ZSTD_freeCCtx(_ctx); }
    ZSTD_CCtx * get() { return _ctx; }
private:
    ZSTD_CCtx * _ctx;
};
class DecompressContext {
public:
    DecompressContext() : _ctx(ZSTD_createDCtx()) {}
    ~DecompressContext() { ZSTD_freeDCtx(_ctx); }
    ZSTD_DCtx * get() { return _ctx; }
private:
    ZSTD_DCtx * _ctx;
};

thread_local std::unique_ptr<CompressContext>  _tlCompressState;
thread_local std::unique_ptr<DecompressContext> _tlDecompressState;

}

size_t ZStdCompressor::adjustProcessLen(uint16_t, size_t len)   const { return ZSTD_compressBound(len); }

bool
ZStdCompressor::process(CompressionConfig config, const void * inputV, size_t inputLen, void * outputV, size_t & outputLenV)
{
    size_t maxOutputLen = ZSTD_compressBound(inputLen);
    if ( ! _tlCompressState) {
        _tlCompressState = std::make_unique<CompressContext>();
    }
    size_t sz = ZSTD_compressCCtx(_tlCompressState->get(), outputV, maxOutputLen, inputV, inputLen, config.compressionLevel);
    assert( ! ZSTD_isError(sz) );
    outputLenV = sz;
    return ! ZSTD_isError(sz);
}

bool
ZStdCompressor::unprocess(const void * inputV, size_t inputLen, void * outputV, size_t & outputLenV)
{
    if ( ! _tlDecompressState) {
        _tlDecompressState = std::make_unique<DecompressContext>();
    }
    size_t sz = ZSTD_decompressDCtx(_tlDecompressState->get(), outputV, outputLenV, inputV, inputLen);
    assert( ! ZSTD_isError(sz) );
    outputLenV = sz;
    return ! ZSTD_isError(sz);
}

}
