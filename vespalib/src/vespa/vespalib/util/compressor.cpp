// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lz4compressor.h"
#include "zstdcompressor.h"
#include <vespa/vespalib/util/memory.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/data/databuffer.h>

using vespalib::alloc::Alloc;

namespace vespalib::compression {

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
    case CompressionConfig::ZSTD:
        {
            ZStdCompressor zstd;
            type = compress(zstd, compression, org, dest);
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
            throw std::runtime_error(make_string("unprocess failed had %zu, wanted %zu, got %zu",
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
        case CompressionConfig::ZSTD:
        {
            ZStdCompressor zstd;
            decompress(zstd, uncompressedLen, org, dest, allowSwap);
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

size_t computeMaxCompressedsize(CompressionConfig::Type type, size_t payloadSize) {
    if (type == CompressionConfig::LZ4) {
        LZ4Compressor lz4;
        return lz4.adjustProcessLen(0, payloadSize);
    } else if (type == CompressionConfig::ZSTD) {
        ZStdCompressor zstd;
        return zstd.adjustProcessLen(0, payloadSize);
    }
    return payloadSize;
}

}
