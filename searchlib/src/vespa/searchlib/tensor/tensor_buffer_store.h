// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    /*
     * Increasing the number of buffers reduces the wasted address space due to buffer sizes being capped to 256 MiB,
     * c.f. ArrayStoreConfig::default_max_buffer_size. This allows for more data to be stored, at the cost of a
     * higher initial memory allocation (BufferAndMeta vector in DataStoreBase), and a higher memory allocation
     * later on as buffers are being used (BufferState).
     *
     * As of 2025-03-10: sizeof(BufferAndMeta) == 24, sizeof(BufferState) == 112.
     *
     * Increasing the maximum buffer size (currently 256 MiB) allows for more data to be stored without a higher
     * initial memory allocation, but the feed latency spike during compaction will be higher.
     *
     * 18 bits for buffer offset => 256 Ki offset limit
     * 14 bits for buffer id     => 16 Ki buffers
     *
     * E.g. tensor<float>(x{},y[128]) uses ~512 bytes per vector. With more than 2 vectors per tensor, the maximum
     * usable offset in the buffer is capped because 256 MiB / 256 Ki = 1024, which is less than the tensor size.
     */
    using RefType = vespalib::datastore::EntryRefT<18>;
    using ArrayStoreType = vespalib::datastore::ArrayStore<char, RefType, TensorBufferTypeMapper>;
    vespalib::eval::ValueType _tensor_type;
    TensorBufferOperations    _ops;
    ArrayStoreType            _array_store;
public:

    static constexpr double array_store_grow_factor = 1.03;
    static constexpr uint32_t array_store_max_type_id = 300;

    TensorBufferStore(const vespalib::eval::ValueType& tensor_type, std::shared_ptr<vespalib::alloc::MemoryAllocator> allocator, uint32_t max_small_subspaces_type_id);
    ~TensorBufferStore();
    void holdTensor(EntryRef ref) override;
    EntryRef move_on_compact(EntryRef ref) override;
    vespalib::MemoryUsage update_stat(const vespalib::datastore::CompactionStrategy& compaction_strategy) override;
    std::unique_ptr<vespalib::datastore::ICompactionContext> start_compact(const vespalib::datastore::CompactionStrategy& compaction_strategy) override;
    EntryRef store_tensor(const vespalib::eval::Value& tensor) override;
    EntryRef store_encoded_tensor(vespalib::nbostream& encoded) override;
    std::unique_ptr<vespalib::eval::Value> get_tensor(EntryRef ref) const override;
    bool encode_stored_tensor(EntryRef ref, vespalib::nbostream& target) const override;
    vespalib::eval::TypedCells get_empty_subspace() const noexcept {
        return _ops.get_empty_subspace();
    }
    VectorBundle get_vectors(EntryRef ref) const noexcept {
        if (!ref.valid()) {
            return {};
        }
        auto buf = _array_store.get(ref);
        return _ops.get_vectors(buf);
    }
    SerializedTensorRef get_serialized_tensor_ref(EntryRef ref) const noexcept {
        if (!ref.valid()) {
            return {};
        }
        auto buf = _array_store.get(ref);
        return _ops.get_serialized_tensor_ref(buf);
    }

    // Used by unit test
    static constexpr uint32_t get_offset_bits() noexcept { return RefType::offset_bits; }
};

}
