// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/data/memorydatastore.h>

#include <vespa/vespalib/util/array.hpp>

namespace vespalib {

using alloc::Alloc;

MemoryDataStore::MemoryDataStore(Alloc&& initialAlloc) : _buffers(), _writePos(0), _lock() {
    _buffers.reserve(24);
    _buffers.emplace_back(std::move(initialAlloc));
}

MemoryDataStore::~MemoryDataStore() = default;

MemoryDataStore::Reference MemoryDataStore::push_back(const void* data, const size_t sz) {
    std::unique_lock guard(_lock);
    const Alloc&     b = _buffers.back();
    if ((sz + _writePos) > b.size()) {
        size_t newSize(std::max(sz, _buffers.back().size() * 2));
        _buffers.emplace_back(b.create(newSize));
        _writePos = 0;
    }
    Alloc&    buf = _buffers.back();
    Reference ref(static_cast<char*>(buf.get()) + _writePos);
    _writePos += sz;
    guard.unlock();
    if (sz > 0) {
        memcpy(ref.data(), data, sz);
    }
    return ref;
}

void MemoryDataStore::clear() noexcept {
    _buffers.clear();
}

} // namespace vespalib
