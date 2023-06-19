// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "array_store_type_mapper.h"
#include <algorithm>
#include <cassert>

namespace vespalib::datastore {

ArrayStoreTypeMapper::ArrayStoreTypeMapper()
    : _array_sizes()
{
}

ArrayStoreTypeMapper::~ArrayStoreTypeMapper() = default;

uint32_t
ArrayStoreTypeMapper::get_type_id(size_t array_size) const
{
    assert(_array_sizes.size() >= 2u);
    if (array_size > _array_sizes.back()) {
        return 0; // type id 0 uses buffer type for large arrays
    }
    auto result = std::lower_bound(_array_sizes.begin() + 1, _array_sizes.end(), array_size);
    assert(result < _array_sizes.end());
    return result - _array_sizes.begin();
}

size_t
ArrayStoreTypeMapper::get_array_size(uint32_t type_id) const
{
    assert(type_id > 0 && type_id < _array_sizes.size());
    return _array_sizes[type_id];
}

uint32_t
ArrayStoreTypeMapper::get_max_type_id(uint32_t max_type_id) const noexcept
{
    auto clamp_type_id = _array_sizes.size() - 1;
    return (clamp_type_id < max_type_id) ? clamp_type_id : max_type_id;
}

}
