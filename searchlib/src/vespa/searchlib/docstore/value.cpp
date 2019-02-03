// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "value.h"
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/util/compressor.h>
#include <vespa/vespalib/xxhash/xxhash.h>

using vespalib::compression::compress;
using vespalib::compression::decompress;

namespace search::docstore {

Value::Value()
    : _syncToken(0),
      _compressedSize(0),
      _uncompressedSize(0),
      _uncompressedCrc(0),
      _compression(CompressionConfig::NONE)
{}

Value::Value(uint64_t syncToken)
    : _syncToken(syncToken),
      _compressedSize(0),
      _uncompressedSize(0),
      _uncompressedCrc(0),
      _compression(CompressionConfig::NONE)
{}

Value::Value(const Value &rhs)
    : _syncToken(rhs._syncToken),
      _compressedSize(rhs._compressedSize),
      _uncompressedSize(rhs._uncompressedSize),
      _uncompressedCrc(rhs._uncompressedCrc),
      _compression(rhs._compression),
      _buf(Alloc::alloc(rhs.size()))
{
    memcpy(get(), rhs.get(), size());
}

void
Value::set(vespalib::DataBuffer &&buf, ssize_t len) {
    set(std::move(buf), len, CompressionConfig());
}

void
Value::set(vespalib::DataBuffer &&buf, ssize_t len, const CompressionConfig &compression) {
    //Underlying buffer must be identical to allow swap.
    vespalib::DataBuffer compressed(buf.getData(), 0u);
    vespalib::ConstBufferRef input(buf.getData(), len);
    CompressionConfig::Type type = compress(compression, input, compressed, true);
    _compressedSize = compressed.getDataLen();
    if (buf.getData() == compressed.getData()) {
        // Uncompressed so we can just steal the underlying buffer.
        buf.stealBuffer().swap(_buf);
    } else {
        compressed.stealBuffer().swap(_buf);
    }
    assert(((type == CompressionConfig::NONE) &&
            (len == ssize_t(_compressedSize))) ||
           ((type != CompressionConfig::NONE) &&
            (len > ssize_t(_compressedSize))));
    _compression = type;
    _uncompressedSize = len;
    _uncompressedCrc = XXH64(input.c_str(), input.size(), 0);
}

Value::Result
Value::decompressed() const {
    vespalib::DataBuffer uncompressed(_buf.get(), (size_t) 0);
    decompress(getCompression(), getUncompressedSize(), vespalib::ConstBufferRef(*this, size()), uncompressed, true);
    uint64_t crc = XXH64(uncompressed.getData(), uncompressed.getDataLen(), 0);
    return std::make_pair<vespalib::DataBuffer, bool>(std::move(uncompressed), crc == _uncompressedCrc);
}

}
