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
                                                              TypeMapper(max_small_buffer_type_id, grow_factor),
                                                              MemoryAllocator::HUGEPAGE_SIZE,
                                                              MemoryAllocator::PAGE_SIZE,
                                                              vespalib::datastore::ArrayStoreConfig::default_max_buffer_size,
                                                              8_Ki, ALLOC_GROW_FACTOR),
                   std::move(allocator), TypeMapper(max_small_buffer_type_id, grow_factor))
{
}

RawBufferStore::~RawBufferStore() = default;

}

namespace vespalib::datastore {

template class ArrayStore<char, EntryRefT<19>, ArrayStoreDynamicTypeMapper<char>>;

}
