// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "compressor.h"
#include <vespa/vespalib/util/memory.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <stdexcept>
#include <lz4.h>
#include <lz4hc.h>

using vespalib::alloc::Alloc;
using vespalib::ConstBufferRef;
using vespalib::DataBuffer;
using vespalib::make_string;

namespace document
{

size_t LZ4Compressor::adjustProcessLen(uint16_t, size_t len)   const { return LZ4_compressBound(len); }
size_t LZ4Compressor::adjustUnProcessLen(uint16_t, size_t len) const { return len; }

bool
LZ4Compressor::process(const CompressionConfig& config, const void * inputV, size_t inputLen, void * outputV, size_t & outputLenV)
{
    const char * input(static_cast<const char *>(inputV));
    char * output(static_cast<char *>(outputV));
    int sz(-1);
    if (config.compressionLevel > 6) {
        Alloc state = Alloc::alloc(LZ4_sizeofStateHC());
        int maxOutputLen = LZ4_compressBound(inputLen);
        sz = LZ4_compress_HC_extStateHC(state.get(), input, output, inputLen, maxOutputLen, config.compressionLevel);
    } else {
        Alloc state = Alloc::alloc(LZ4_sizeofState());
        sz = LZ4_compress_withState(state.get(), input, output, inputLen);
    }
    if (sz != 0) {
        outputLenV = sz;
    }
    assert(sz != 0);
    return (sz != 0);
    
}

bool
LZ4Compressor::unprocess(const void * inputV, size_t inputLen, void * outputV, size_t & outputLenV)
{
    const char * input(static_cast<const char *>(inputV));
    char * output(static_cast<char *>(outputV));
    int sz = LZ4_decompress_safe(input, output, inputLen, outputLenV);
    if (sz > 0) {
        outputLenV = sz;
    }
    assert(sz > 0);
    return (sz > 0);
}

CompressionConfig::Type
compress(ICompressor & compressor, const CompressionConfig & compression, const ConstBufferRef & org, DataBuffer & dest)
{
    CompressionConfig::Type type(CompressionConfig::NONE);
    dest.ensureFree(compressor.adjustProcessLen(0, org.size()));
    size_t compressedSize(dest.getFreeLen());
    if (compressor.process(compression, org.c_str(), org.size(), dest.getFree(), compressedSize)) {
        if (compressedSize < ((org.size() * compression.threshold)/100)) {
            dest.moveFreeToData(compressedSize);
            type = compression.type;
        }
    }
    return type;
}

CompressionConfig::Type
docompress(const CompressionConfig & compression, const ConstBufferRef & org, DataBuffer & dest)
{
    CompressionConfig::Type type(CompressionConfig::NONE);
    switch (compression.type) {
    case CompressionConfig::LZ4:
        {
            LZ4Compressor lz4;
            type = compress(lz4, compression, org, dest);
        }
        break;
    case CompressionConfig::NONE:
    default:
        break;
    }
    return type;
}

CompressionConfig::Type
compress(const CompressionConfig & compression, const ConstBufferRef & org, DataBuffer & dest, bool allowSwap)
{
    CompressionConfig::Type type(CompressionConfig::NONE);
    if (org.size() >= compression.minSize) {
        type = docompress(compression, org, dest);
    }
    if (type == CompressionConfig::NONE) {
        if (allowSwap) {
            DataBuffer tmp(const_cast<char *>(org.c_str()), org.size());
            tmp.moveFreeToData(org.size());
            dest.swap(tmp);
        } else {
            dest.writeBytes(org.c_str(), org.size());
        }
    }
    return type;
}


void
decompress(ICompressor & decompressor, size_t uncompressedLen, const ConstBufferRef & org, DataBuffer & dest, bool allowSwap)
{
    dest.ensureFree(uncompressedLen);
    size_t realUncompressedLen(dest.getFreeLen());
    if ( ! decompressor.unprocess(org.c_str(), org.size(), dest.getFree(), realUncompressedLen) ) {
        if ( uncompressedLen < realUncompressedLen) {
            if (allowSwap) {
                DataBuffer tmp(const_cast<char *>(org.c_str()), org.size());
                tmp.moveFreeToData(org.size());
                dest.swap(tmp);
            } else {
                dest.writeBytes(org.c_str(), org.size());
            }
        } else {
            throw std::runtime_error(make_string("unprocess failed had %" PRIu64 ", wanted %" PRId64 ", got %" PRIu64,
                                                 org.size(), uncompressedLen, realUncompressedLen));
        }
    } else {
        dest.moveFreeToData(realUncompressedLen);
    }
}

void
decompress(const CompressionConfig::Type & type, size_t uncompressedLen, const ConstBufferRef & org, DataBuffer & dest, bool allowSwap)
{
    switch (type) {
    case CompressionConfig::LZ4:
        {
            LZ4Compressor lz4;
            decompress(lz4, uncompressedLen, org, dest, allowSwap);
        }
        break;
    case CompressionConfig::NONE:
    case CompressionConfig::UNCOMPRESSABLE:
        if (allowSwap) {
            DataBuffer tmp(const_cast<char *>(org.c_str()), org.size());
            tmp.moveFreeToData(org.size());
            dest.swap(tmp);
        } else {
            dest.writeBytes(org.c_str(), org.size());
        }
        break;
    default:
        throw std::runtime_error(make_string("Unable to handle decompression of type '%d'", type));
        break;
    }
}

}
