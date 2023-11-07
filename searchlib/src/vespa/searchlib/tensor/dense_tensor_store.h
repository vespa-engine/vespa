// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_store.h"
#include "empty_subspace.h"
#include "vector_bundle.h"
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/typed_cells.h>
#include <vespa/vespalib/datastore/datastore.h>

namespace vespalib::eval { struct Value; }

namespace search::tensor {

/**
 * Class for storing dense tensors with known bounds in memory, used
 * by DenseTensorAttribute.
 */
class DenseTensorStore : public TensorStore
{
public:
    // 4 Ki buffers of 256 MiB each is 1 TiB.
    using RefType = vespalib::datastore::EntryRefT<20>;
    using DataStoreType = vespalib::datastore::DataStoreT<RefType>;
    using ValueType = vespalib::eval::ValueType;
    static constexpr size_t max_dense_tensor_buffer_size = 256_Mi;

    struct TensorSizeCalc
    {
        size_t   _numCells; // product of dimension sizes
        vespalib::eval::CellType _cell_type;
        size_t   _aligned_size;

        explicit TensorSizeCalc(const ValueType &type);
        size_t bufSize() const {
            return vespalib::eval::CellTypeUtils::mem_size(_cell_type, _numCells);
        }
        size_t alignedSize() const noexcept { return _aligned_size; }
    };

    class BufferType : public vespalib::datastore::BufferType<char>
    {
        using CleanContext = vespalib::datastore::BufferType<char>::CleanContext;
        std::shared_ptr<vespalib::alloc::MemoryAllocator> _allocator;
    public:
        BufferType(const TensorSizeCalc &tensorSizeCalc, std::shared_ptr<vespalib::alloc::MemoryAllocator> allocator);
        ~BufferType() override;
        void clean_hold(void *buffer, size_t offset, EntryCount num_entries, CleanContext cleanCtx) override;
        const vespalib::alloc::MemoryAllocator* get_memory_allocator() const override;
    };
private:
    DataStoreType _concreteStore;
    TensorSizeCalc _tensorSizeCalc;
    BufferType _bufferType;
    ValueType _type; // type of dense tensor
    SubspaceType  _subspace_type;
    EmptySubspace _empty;
public:
    DenseTensorStore(const ValueType &type, std::shared_ptr<vespalib::alloc::MemoryAllocator> allocator);
    ~DenseTensorStore() override;

    const ValueType &type() const noexcept { return _type; }
    size_t getNumCells() const noexcept { return _tensorSizeCalc._numCells; }
    size_t getBufSize() const { return _tensorSizeCalc.bufSize(); }
    const void *getRawBuffer(RefType ref) const noexcept {
        return _store.getEntryArray<char>(ref, _bufferType.getArraySize());
    }
    vespalib::datastore::Handle<char> allocRawBuffer();
    void holdTensor(EntryRef ref) override;
    EntryRef move_on_compact(EntryRef ref) override;
    vespalib::MemoryUsage update_stat(const vespalib::datastore::CompactionStrategy& compaction_strategy) override;
    std::unique_ptr<vespalib::datastore::ICompactionContext> start_compact(const vespalib::datastore::CompactionStrategy& compaction_strategy) override;
    EntryRef store_tensor(const vespalib::eval::Value &tensor) override;
    EntryRef store_encoded_tensor(vespalib::nbostream &encoded) override;
    std::unique_ptr<vespalib::eval::Value> get_tensor(EntryRef ref) const override;
    bool encode_stored_tensor(EntryRef ref, vespalib::nbostream &target) const override;
    const DenseTensorStore* as_dense() const override;
    DenseTensorStore* as_dense() override;

    vespalib::eval::TypedCells get_typed_cells(EntryRef ref) const noexcept {
        if (!ref.valid()) [[unlikely]] {
            return _empty.cells();
        }
        return {getRawBuffer(ref), _type.cell_type(), getNumCells()};
    }
    VectorBundle get_vectors(EntryRef ref) const noexcept {
        if (!ref.valid()) [[unlikely]] {
            return {};
        }
        return {getRawBuffer(ref), 1, _subspace_type};
    }
    const SubspaceType& get_subspace_type() const noexcept { return _subspace_type; }
    // The following methods are meant to be used only for unit tests.
    uint32_t getArraySize() const noexcept { return _bufferType.getArraySize(); }
    uint32_t get_max_buffer_entries() const noexcept { return _bufferType.get_max_entries(); }
};

}
