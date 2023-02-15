// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_buffer_type_mapper.h"
#include "tensor_buffer_operations.h"
#include <algorithm>
#include <cmath>
#include <limits>

namespace search::tensor {

TensorBufferTypeMapper::TensorBufferTypeMapper()
    : _array_sizes(),
      _ops(nullptr)
{
}

TensorBufferTypeMapper::TensorBufferTypeMapper(uint32_t max_small_subspaces_type_id, double grow_factor, TensorBufferOperations* ops)
    : _array_sizes(),
      _ops(ops)
{
    _array_sizes.reserve(max_small_subspaces_type_id + 1);
    _array_sizes.emplace_back(0); // type id 0 uses LargeSubspacesBufferType
    uint32_t num_subspaces = 0;
    size_t prev_array_size = 0u;
    size_t array_size = 0u;
    for (uint32_t type_id = 1; type_id <= max_small_subspaces_type_id; ++type_id) {
        if (type_id > 1) {
            num_subspaces = std::max(num_subspaces +  1, static_cast<uint32_t>(std::floor(num_subspaces * grow_factor)));
        }
        array_size = _ops->get_buffer_size(num_subspaces);
        while (array_size <= prev_array_size) {
            ++num_subspaces;
            array_size = _ops->get_buffer_size(num_subspaces);
        }
        if (array_size > std::numeric_limits<uint32_t>::max()) {
            break;
        }
        _array_sizes.emplace_back(array_size);
        prev_array_size = array_size;
    }
}

TensorBufferTypeMapper::~TensorBufferTypeMapper() = default;

uint32_t
TensorBufferTypeMapper::get_type_id(size_t array_size) const
{
    assert(!_array_sizes.empty());
    auto result = std::lower_bound(_array_sizes.begin() + 1, _array_sizes.end(), array_size);
    if (result == _array_sizes.end()) {
        return 0; // type id 0 uses LargeSubspacesBufferType
    }
    return result - _array_sizes.begin();
}

size_t
TensorBufferTypeMapper::get_array_size(uint32_t type_id) const
{
    assert(type_id > 0 && type_id < _array_sizes.size());
    return _array_sizes[type_id];
}

uint32_t
TensorBufferTypeMapper::get_max_small_array_type_id(uint32_t max_small_array_type_id) const noexcept
{
    auto clamp_type_id = _array_sizes.size() - 1;
    return (clamp_type_id < max_small_array_type_id) ? clamp_type_id : max_small_array_type_id;
}

}
