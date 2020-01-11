// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "value.h"
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/util/compressor.h>
#include <xxhash.h>

using vespalib::compression::compress;
using vespalib::compression::decompress;

namespace search::docstore {

Value::Value()
    : _syncToken(0),
      _uncompressedCrc(0),
      _compressedSize(0),
      _uncompressedSize(0),
      _buf(),
      _compression(CompressionConfig::NONE)
{}

Value::Value(uint64_t syncToken)
    : _syncToken(syncToken),
      _uncompressedCrc(0),
      _compressedSize(0),
      _uncompressedSize(0),
      _buf(),
      _compression(CompressionConfig::NONE)
{}

Value::Value(const Value &rhs) = default;

Value::~Value() = default;

const void *
Value::get() const {
    return _buf ? _buf->get() : nullptr;
}

void
Value::set(vespalib::DataBuffer &&buf, ssize_t len) {
    set(std::move(buf), len, CompressionConfig());
}

namespace {

vespalib::alloc::Alloc
compact(size_t sz, vespalib::alloc::Alloc buf) {
    if (sz <= (buf.size() << 2)) {
        vespalib::alloc::Alloc shrunk = buf.create(sz);
        memcpy(shrunk.get(), buf.get(), sz);
        return shrunk;
    }
    return buf;
}

}
void
Value::set(vespalib::DataBuffer &&buf, ssize_t len, const CompressionConfig &compression) {
    assert(len < std::numeric_limits<uint32_t>::max());
    //Underlying buffer must be identical to allow swap.
    vespalib::DataBuffer compressed(buf.getData(), 0u);
    vespalib::ConstBufferRef input(buf.getData(), len);
    CompressionConfig::Type type = compress(compression, input, compressed, true);
    _compressedSize = compressed.getDataLen();
    _compression = type;
    _uncompressedSize = len;
    _uncompressedCrc = XXH64(input.c_str(), input.size(), 0);
    _buf = std::make_shared<Alloc>(compact(_compressedSize,
            (buf.getData() == compressed.getData()) ? buf.stealBuffer() : compressed.stealBuffer()));

    assert(((type == CompressionConfig::NONE) &&
            (len == ssize_t(_compressedSize))) ||
           ((type != CompressionConfig::NONE) &&
            (len > ssize_t(_compressedSize))));
}

Value::Result
Value::decompressed() const {
    vespalib::DataBuffer uncompressed(_buf.get(), (size_t) 0);
    decompress(getCompression(), getUncompressedSize(), vespalib::ConstBufferRef(*this, size()), uncompressed, true);
    uint64_t crc = XXH64(uncompressed.getData(), uncompressed.getDataLen(), 0);
    return std::make_pair<vespalib::DataBuffer, bool>(std::move(uncompressed), crc == _uncompressedCrc);
}

}
