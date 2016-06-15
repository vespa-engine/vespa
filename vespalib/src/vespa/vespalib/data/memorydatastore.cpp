// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/data/memorydatastore.h>

namespace vespalib {

MemoryDataStore::MemoryDataStore(size_t initialSize) :
    _buffers()
{
    _buffers.reserve(24);
    Buffer buf;
    _buffers.push_back(buf);
    _buffers.back().reserve(initialSize);
}

MemoryDataStore::~MemoryDataStore()
{
}

MemoryDataStore::Reference
MemoryDataStore::push_back(const void * data, const size_t sz)
{
    const Buffer & b = _buffers.back();
    if (sz > (b.capacity() - b.size())) {
        size_t newSize(std::max(sz, _buffers.back().size()*2));
        Buffer buf;
        assert(_buffers.capacity() >= (_buffers.size() + 1));
        _buffers.push_back(buf);
        _buffers.back().reserve(newSize);
    }
    Buffer & buf = _buffers.back();
    const char * start = static_cast<const char *>(data);
    for (uint32_t i(0); i < sz; i++) {
        buf.push_back_fast(start[i]);
    }
    return Reference(&buf[buf.size() - sz]);
}

VariableSizeVector::VariableSizeVector(size_t initialSize) :
    _vector(),
    _store(initialSize)
{
}

VariableSizeVector::~VariableSizeVector()
{
}

VariableSizeVector::Reference
VariableSizeVector::push_back(const void * data, const size_t sz)
{
    MemoryDataStore::Reference ptr(_store.push_back(data, sz));
    _vector.push_back(Reference(ptr.data(), sz));
    return _vector.back();
}

} // namespace vespalib
