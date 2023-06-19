// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "array_store_type_mapper.h"
#include "atomic_entry_ref.h"

namespace vespalib::datastore {

template <typename EntryT> class SmallArrayBufferType;
template <typename EntryT> class DynamicArrayBufferType;
template <typename EntryT> class LargeArrayBufferType;

/*
 * This class provides mapping between type ids and array sizes needed for
 * storing values.
 *
 * Type ids [1;max_static_array_buffer_type_id] use SmallBufferType,
 * containing small arrays where buffer type specifies array size.
 *
 * Type ids [max_static_array_buffer_type_id+1;max_buffer_type_id] use
 * DynamicBufferType, containing medium sized arrays where the same
 * buffer type handles a range of array sizes and actual array size is
 * also stored in the entry.
 *
 * Type id 0 uses LargeBufferType, which handles any array size but uses
 * heap allocation.
 */
template <typename ElemT>
class ArrayStoreDynamicTypeMapper : public vespalib::datastore::ArrayStoreTypeMapper
{
    uint32_t _max_static_array_buffer_type_id;
public:
    using SmallBufferType = vespalib::datastore::SmallArrayBufferType<ElemT>;
    using DynamicBufferType = vespalib::datastore::DynamicArrayBufferType<ElemT>;
    using LargeBufferType = vespalib::datastore::LargeArrayBufferType<ElemT>;

    ArrayStoreDynamicTypeMapper();
    ArrayStoreDynamicTypeMapper(uint32_t max_buffer_type_id, double grow_factor);
    ~ArrayStoreDynamicTypeMapper();
    void setup_array_sizes(uint32_t max_buffer_type_id, double grow_factor);
    size_t get_entry_size(uint32_t type_id) const;
    bool is_dynamic_buffer(uint32_t type_id) const noexcept { return type_id > _max_static_array_buffer_type_id; }
    uint32_t count_dynamic_buffer_types(uint32_t max_type_id) const noexcept { return (max_type_id > _max_static_array_buffer_type_id) ? (max_type_id - _max_static_array_buffer_type_id) : 0u; }
};

extern template class ArrayStoreDynamicTypeMapper<char>;
extern template class ArrayStoreDynamicTypeMapper<int8_t>;
extern template class ArrayStoreDynamicTypeMapper<int16_t>;
extern template class ArrayStoreDynamicTypeMapper<int32_t>;
extern template class ArrayStoreDynamicTypeMapper<int64_t>;
extern template class ArrayStoreDynamicTypeMapper<float>;
extern template class ArrayStoreDynamicTypeMapper<double>;
extern template class ArrayStoreDynamicTypeMapper<AtomicEntryRef>;

}
