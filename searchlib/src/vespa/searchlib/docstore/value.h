// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/compressionconfig.h>
#include <vespa/vespalib/data/databuffer.h>

namespace search::docstore {

class Value {
public:
    using Alloc = vespalib::alloc::Alloc;
    using CompressionConfig = vespalib::compression::CompressionConfig;

    Value();
    Value(uint64_t syncToken);

    Value(Value &&rhs) = default;
    Value &operator=(Value &&rhs) = default;

    Value(const Value &rhs);

    void setCompression(CompressionConfig::Type comp, size_t uncompressedSize);
    uint64_t getSyncToken() const { return _syncToken; }
    CompressionConfig::Type getCompression() const { return _compression; }
    size_t getUncompressedSize() const { return _uncompressedSize; }

    /**
     * Compress buffer into temporary buffer and copy temporary buffer to
     * value along with compression config.
     */
    void set(vespalib::DataBuffer &&buf, ssize_t len, const CompressionConfig &compression);
    // Keep buffer uncompressed
    void set(vespalib::DataBuffer &&buf, ssize_t len);

    vespalib::DataBuffer decompressed() const;

    size_t size() const { return _compressedSize; }
    bool empty() const { return size() == 0; }
    operator const void *() const { return _buf.get(); }
    const void *get() const { return _buf.get(); }
    void *get() { return _buf.get(); }
private:
    uint64_t _syncToken;
    size_t _compressedSize;
    size_t _uncompressedSize;
    CompressionConfig::Type _compression;
    Alloc _buf;
};

}
