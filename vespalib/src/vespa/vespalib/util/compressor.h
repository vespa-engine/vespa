// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "compressionconfig.h"
#include "buffer.h"
#include "alloc.h"

namespace vespalib { class DataBuffer; }

namespace vespalib::compression {

class ICompressor
{
public:
    virtual ~ICompressor() { }
    virtual bool process(CompressionConfig config, const void * input, size_t inputLen, void * output, size_t & outputLen) = 0;
    virtual bool unprocess(const void * input, size_t inputLen, void * output, size_t & outputLen) = 0;
    virtual size_t adjustProcessLen(uint16_t options, size_t len)   const = 0;
};

/**
 * Will try to compress a buffer according to the config. If the criteria can not
 * be met it will return NONE and dest will get the input buffer.
 * @param compression is config for how to compress and what criteria to meet.
 * @param org is the original input buffer.
 * @param dest is the destination buffer. The compressed data will be appended unless allowSwap is true
 *             and it is not compressable. Then it will be swapped in.
 * @param allowSwap will tell it the data must be appended or if it can be swapped in if it is uncompressable or config is NONE.
 */
CompressionConfig::Type compress(CompressionConfig::Type compression, const ConstBufferRef & org, DataBuffer & dest, bool allowSwap);
CompressionConfig::Type compress(CompressionConfig compression, const vespalib::ConstBufferRef & org, vespalib::DataBuffer & dest, bool allowSwap);

/**
 * Will try to decompress a buffer according to the config.
 * be met it will return NONE and dest will get the input buffer.
 * @param compression is the compression type used for the buffer.
 * @param uncompressedLen is the length of the uncompressed data.
 * @param org is the original input buffer.
 * @param dest is the destination buffer. The decompressed data will be
 *             appended unless allowSwap is true and compression is NONE.
 *             Then it will be swapped in.
 * @param allowSwap will tell it the data must be appended or if it can be swapped in if compression type is NONE.
 */
void decompress(CompressionConfig::Type compression, size_t uncompressedLen, const vespalib::ConstBufferRef & org, vespalib::DataBuffer & dest, bool allowSwap);

size_t computeMaxCompressedsize(CompressionConfig::Type type, size_t uncompressedSize);

//-----------------------------------------------------------------------------

/**
 * Simple utility used to compress data according to a compression
 * configuration.
 **/
class Compress {
private:
    alloc::Alloc            _space;
    CompressionConfig::Type _type;
    const char             *_data;
    size_t                  _size;
public:
    Compress(CompressionConfig config, const char *uncompressed_data, size_t uncompressed_size);
    CompressionConfig::Type type() const { return _type; }
    const char *data() const { return _data; }
    size_t size() const { return _size; }
};

/**
 * Simple utility used to decompress data using additional information
 * about compression type and uncompressed size.
 **/
class Decompress {
private:
    alloc::Alloc _space;
    const char  *_data;
    size_t       _size;
public:
    Decompress(CompressionConfig::Type type, size_t uncompressed_size,
               const char *compressed_data, size_t compressed_size);
    const char *data() const { return _data; }
    size_t size() const { return _size; }
};

//-----------------------------------------------------------------------------

}
