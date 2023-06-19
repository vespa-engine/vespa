// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "large_array_buffer_type.h"
#include "small_array_buffer_type.h"

namespace vespalib::datastore {

/**
 * This class provides a 1-to-1 mapping between type ids and array sizes for small arrays in an array store.
 *
 * This means that buffers for type id 1 stores arrays of size 1, buffers for type id 2 stores arrays of size 2, and so on.
 * Type id 0 is always reserved for large arrays allocated on the heap.
 *
 * A more complex mapping can be used by creating a custom mapper and BufferType implementations.
 */
template <typename ElemT>
class ArrayStoreSimpleTypeMapper {
public:
    using SmallBufferType = SmallArrayBufferType<ElemT>;
    using LargeBufferType = LargeArrayBufferType<ElemT>;

    uint32_t get_type_id(size_t array_size) const noexcept { return array_size; }
    size_t get_array_size(uint32_t type_id) const noexcept { return type_id; }
    size_t get_entry_size(uint32_t type_id) const noexcept { return get_array_size(type_id) * sizeof(ElemT); }
    static uint32_t get_max_type_id(uint32_t max_type_id) noexcept { return max_type_id; }
};

}
