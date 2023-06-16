// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "array_store_dynamic_type_mapper.h"
#include "dynamic_array_buffer_type.h"
#include "aligner.h"
#include <algorithm>
#include <cmath>
#include <limits>
#include <optional>

namespace vespalib::datastore {

template <typename ElemT>
ArrayStoreDynamicTypeMapper<ElemT>::ArrayStoreDynamicTypeMapper()
    : ArrayStoreTypeMapper(),
      _max_static_array_buffer_type_id(0)
{
}

template <typename ElemT>
ArrayStoreDynamicTypeMapper<ElemT>::ArrayStoreDynamicTypeMapper(uint32_t max_buffer_type_id, double grow_factor)
    : ArrayStoreTypeMapper(),
      _max_static_array_buffer_type_id(0)
{
    setup_array_sizes(max_buffer_type_id, grow_factor);
}

template <typename ElemT>
void
ArrayStoreDynamicTypeMapper<ElemT>::setup_array_sizes(uint32_t max_buffer_type_id, double grow_factor)
{
    _array_sizes.clear();
    _array_sizes.reserve(max_buffer_type_id + 1);
    _array_sizes.emplace_back(0); // type id 0 is fallback for large arrays
    size_t array_size = 1u;
    size_t entry_size = sizeof(ElemT);
    bool dynamic_arrays = false;
    for (uint32_t type_id = 1; type_id <= max_buffer_type_id; ++type_id) {
        if (type_id > 1) {
            array_size = std::max(array_size +  1, static_cast<size_t>(std::floor(array_size * grow_factor)));
            if (array_size > _array_sizes.back() + 1 || dynamic_arrays) {
                if (!dynamic_arrays) {
                    _max_static_array_buffer_type_id = type_id - 1;
                    dynamic_arrays = true;
                }
                entry_size = DynamicBufferType::calc_entry_size(array_size);
                array_size = DynamicBufferType::calc_array_size(entry_size);
            } else {
                entry_size = array_size * sizeof(ElemT);
            }
        }
        if (entry_size > std::numeric_limits<uint32_t>::max()) {
            break;
        }
        _array_sizes.emplace_back(array_size);
    }
    if (!dynamic_arrays) {
        _max_static_array_buffer_type_id = _array_sizes.size() - 1;
    }
}

template <typename ElemT>
ArrayStoreDynamicTypeMapper<ElemT>::~ArrayStoreDynamicTypeMapper() = default;

template <typename ElemT>
size_t
ArrayStoreDynamicTypeMapper<ElemT>::get_entry_size(uint32_t type_id) const
{
    auto array_size = get_array_size(type_id);
    if (type_id <= _max_static_array_buffer_type_id) {
        return array_size * sizeof(ElemT);
    } else {
        return DynamicBufferType::calc_entry_size(array_size);
    }
}

}
