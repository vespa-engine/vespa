// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <cstdint>
#include <vector>

namespace search::tensor {

class LargeSubspacesBufferType;
class SmallSubspacesBufferType;
class TensorBufferOperations;

/*
 * This class provides mapping between type ids and array sizes needed for
 * storing a tensor.
 */
class TensorBufferTypeMapper
{
    std::vector<size_t> _array_sizes;
    TensorBufferOperations* _ops;
public:
    using SmallBufferType = SmallSubspacesBufferType;
    using LargeBufferType = LargeSubspacesBufferType;

    TensorBufferTypeMapper();
    TensorBufferTypeMapper(uint32_t max_small_subspaces_type_id, TensorBufferOperations* ops);
    ~TensorBufferTypeMapper();

    uint32_t get_type_id(size_t array_size) const;
    size_t get_array_size(uint32_t type_id) const;
    TensorBufferOperations& get_tensor_buffer_operations() const noexcept { return *_ops; }
};

}
