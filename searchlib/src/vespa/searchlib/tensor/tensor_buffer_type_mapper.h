// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/array_store_type_mapper.h>

namespace search::tensor {

class LargeSubspacesBufferType;
class SmallSubspacesBufferType;
class TensorBufferOperations;

/*
 * This class provides mapping between type ids and array sizes needed for
 * storing a tensor.
 */
class TensorBufferTypeMapper : public vespalib::datastore::ArrayStoreTypeMapper
{
    TensorBufferOperations* _ops;
public:
    using SmallBufferType = SmallSubspacesBufferType;
    using LargeBufferType = LargeSubspacesBufferType;

    TensorBufferTypeMapper();
    TensorBufferTypeMapper(uint32_t max_small_subspaces_type_id, double grow_factor, TensorBufferOperations* ops);
    ~TensorBufferTypeMapper();

    TensorBufferOperations& get_tensor_buffer_operations() const noexcept { return *_ops; }
    size_t get_entry_size(uint32_t type_id) const { return get_array_size(type_id); }
};

}
