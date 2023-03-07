// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "raw_buffer_store.h"
#include <vespa/vespalib/datastore/array_store.hpp>
#include <cassert>

using vespalib::alloc::MemoryAllocator;
using vespalib::datastore::EntryRef;

namespace {

constexpr float ALLOC_GROW_FACTOR = 0.2;

}

namespace search::attribute {

RawBufferStore::RawBufferStore(std::shared_ptr<vespalib::alloc::MemoryAllocator> allocator, uint32_t max_small_buffer_type_id, double grow_factor)
    : _array_store(ArrayStoreType::optimizedConfigForHugePage(max_small_buffer_type_id,
                                                              RawBufferTypeMapper(max_small_buffer_type_id, grow_factor),
                                                              MemoryAllocator::HUGEPAGE_SIZE,
                                                              MemoryAllocator::PAGE_SIZE,
                                                              8_Ki, ALLOC_GROW_FACTOR),
                   std::move(allocator), RawBufferTypeMapper(max_small_buffer_type_id, grow_factor))
{
}

RawBufferStore::~RawBufferStore() = default;

vespalib::ConstArrayRef<char>
RawBufferStore::get(EntryRef ref) const
{
    auto array = _array_store.get(ref);
    uint32_t size = 0;
    assert(array.size() >= sizeof(size));
    memcpy(&size, array.data(), sizeof(size));
    assert(array.size() >= sizeof(size) + size);
    return {array.data() + sizeof(size), size};
}

EntryRef
RawBufferStore::set(vespalib::ConstArrayRef<char> raw)
{
    uint32_t size = raw.size();
    if (size == 0) {
        return EntryRef();
    }
    size_t buffer_size = raw.size() + sizeof(size);
    auto& mapper = _array_store.get_mapper();
    auto type_id = mapper.get_type_id(buffer_size);
    auto array_size = (type_id != 0) ? mapper.get_array_size(type_id) : buffer_size;
    assert(array_size >= buffer_size);
    auto ref = _array_store.allocate(array_size);
    auto buf = _array_store.get_writable(ref);
    memcpy(buf.data(), &size, sizeof(size));
    memcpy(buf.data() + sizeof(size), raw.data(), size);
    if (array_size > buffer_size) {
        memset(buf.data() + buffer_size, 0, array_size - buffer_size);
    }
    return ref;
}

std::unique_ptr<vespalib::datastore::ICompactionContext>
RawBufferStore::start_compact(const vespalib::datastore::CompactionStrategy& compaction_strategy)
{
    return _array_store.compact_worst(compaction_strategy);
}

}
