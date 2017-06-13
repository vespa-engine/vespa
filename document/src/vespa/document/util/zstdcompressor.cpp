// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "zstdcompressor.h"
#include <vespa/vespalib/util/alloc.h>
#include <vespa/vespalib/util/sync.h>
#include <zstd.h>
#include <vector>
#include <cassert>

using vespalib::alloc::Alloc;

namespace document {

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

__thread CompressContext * _tlCompressState = nullptr;
__thread DecompressContext * _tlDecompressState = nullptr;


using CompressStateUP = std::unique_ptr<CompressContext>;
using DecompressStateUP = std::unique_ptr<DecompressContext>;
vespalib::Lock _G_Mutex;
std::vector<CompressStateUP> _G_compressRegistry;
std::vector<DecompressStateUP> _G_decompressRegistry;

}

size_t ZStdCompressor::adjustProcessLen(uint16_t, size_t len)   const { return ZSTD_compressBound(len); }

bool
ZStdCompressor::process(const CompressionConfig& config, const void * inputV, size_t inputLen, void * outputV, size_t & outputLenV)
{
    size_t maxOutputLen = ZSTD_compressBound(inputLen);
    if (_tlCompressState == nullptr) {
        vespalib::LockGuard guard(_G_Mutex);
        CompressStateUP context = std::make_unique<CompressContext>();
        _tlCompressState = context.get();
        _G_compressRegistry.push_back(std::move(context));
    }
    size_t sz = ZSTD_compressCCtx(_tlCompressState->get(), outputV, maxOutputLen, inputV, inputLen, config.compressionLevel);
    assert( ! ZSTD_isError(sz) );
    outputLenV = sz;
    return ! ZSTD_isError(sz);
    
}

bool
ZStdCompressor::unprocess(const void * inputV, size_t inputLen, void * outputV, size_t & outputLenV)
{
    if (_tlDecompressState == nullptr) {
        vespalib::LockGuard guard(_G_Mutex);
        DecompressStateUP context = std::make_unique<DecompressContext>();
        _tlDecompressState = context.get();
        _G_decompressRegistry.push_back(std::move(context));
    }
    size_t sz = ZSTD_decompressDCtx(_tlDecompressState->get(), outputV, outputLenV, inputV, inputLen);
    assert( ! ZSTD_isError(sz) );
    outputLenV = sz;
    return ! ZSTD_isError(sz);
}

}
