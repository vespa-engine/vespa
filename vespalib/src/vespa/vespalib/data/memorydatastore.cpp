// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/data/memorydatastore.h>

#include <vespa/vespalib/util/array.hpp>

namespace vespalib {

using alloc::Alloc;

MemoryDataStore::MemoryDataStore(Alloc&& initialAlloc)
    : _buffers(), _writePos(0), _lock(), _tracker(), _transient_memory(0) {
    _buffers.reserve(24);
    _buffers.emplace_back(std::move(initialAlloc));
}

MemoryDataStore::~MemoryDataStore() {
    clear();
}

std::span<std::byte> MemoryDataStore::alloc(size_t size) {
    std::unique_lock guard(_lock);
    const Alloc&     b = _buffers.back();
    if (size + _writePos > b.size()) {
        size_t newSize(std::max(size, _buffers.back().size() * 2));
        _buffers.emplace_back(b.create(newSize));
        _writePos = 0;
    }
    Alloc&               buf = _buffers.back();
    std::span<std::byte> ref(static_cast<std::byte*>(buf.get()) + _writePos, size);
    _writePos += size;
    _transient_memory += size;
    _tracker.set_transient_memory(_transient_memory, 2_Mi);
    return ref;
}

std::span<const std::byte> MemoryDataStore::push_back(std::span<const std::byte> src) {
    auto dst = alloc(src.size());
    if (src.size() > 0) {
        memcpy(dst.data(), src.data(), src.size());
    }
    return dst;
}

void MemoryDataStore::clear() noexcept {
    auto lock = _tracker.acquire_lock();
    _buffers.clear();
    _transient_memory = 0;
    _tracker.set_transient_memory(std::move(lock), 0);
}

} // namespace vespalib
