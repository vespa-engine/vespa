// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_store.h"
#include "tensor_buffer_operations.h"
#include "tensor_buffer_type_mapper.h"
#include "large_subspaces_buffer_type.h"
#include "small_subspaces_buffer_type.h"
#include <vespa/eval/eval/value_type.h>
#include <vespa/vespalib/datastore/array_store.h>

namespace search::tensor {

/**
 * Class for storing tensor buffers in memory and making tensor views
 * based on stored tensor buffer.
 */
class TensorBufferStore : public TensorStore
{
    using RefType = vespalib::datastore::EntryRefT<19>;
    using ArrayStoreType = vespalib::datastore::ArrayStore<char, RefType, TensorBufferTypeMapper>;
    vespalib::eval::ValueType _tensor_type;
    TensorBufferOperations    _ops;
    ArrayStoreType            _array_store;
public:
    TensorBufferStore(const vespalib::eval::ValueType& tensor_type, std::shared_ptr<vespalib::alloc::MemoryAllocator> allocator, uint32_t max_small_subspaces_type_id);
    ~TensorBufferStore();
    void holdTensor(EntryRef ref) override;
    EntryRef move(EntryRef ref) override;
    EntryRef store_tensor(const vespalib::eval::Value &tensor);
    EntryRef store_encoded_tensor(vespalib::nbostream &encoded);
    std::unique_ptr<vespalib::eval::Value> get_tensor(EntryRef ref) const;
    bool encode_stored_tensor(EntryRef ref, vespalib::nbostream &target) const;
};

}
