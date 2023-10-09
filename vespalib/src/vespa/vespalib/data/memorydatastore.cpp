// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/data/memorydatastore.h>
#include <vespa/vespalib/util/array.hpp>

namespace vespalib {

using alloc::Alloc;
using LockGuard = std::lock_guard<std::mutex>;

MemoryDataStore::MemoryDataStore(Alloc && initialAlloc, std::mutex * lock)
    : _buffers(),
      _writePos(0),
      _lock(lock)
{
    _buffers.reserve(24);
    _buffers.emplace_back(std::move(initialAlloc));
}

MemoryDataStore::~MemoryDataStore() = default;

MemoryDataStore::Reference
MemoryDataStore::push_back(const void * data, const size_t sz)
{
    std::unique_ptr<LockGuard> guard;
    if  (_lock != nullptr) {
        guard = std::make_unique<LockGuard>(*_lock);
    }
    const Alloc & b = _buffers.back();
    if ((sz + _writePos) > b.size()) {
        size_t newSize(std::max(sz, _buffers.back().size()*2));
        _buffers.emplace_back(b.create(newSize));
        _writePos = 0;
    }
    Alloc & buf = _buffers.back();
    Reference ref(static_cast<char *>(buf.get()) + _writePos);
    _writePos += sz;
    guard.reset();
    if (sz > 0) {
        memcpy(ref.data(), data, sz);
    }
    return ref;
}

} // namespace vespalib
