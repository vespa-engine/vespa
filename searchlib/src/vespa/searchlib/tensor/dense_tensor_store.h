// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_store.h"
#include <vespa/eval/eval/value_type.h>

namespace vespalib { namespace tensor { class MutableDenseTensorView; }}

namespace search::tensor {

/**
 * Class for storing dense tensors with known bounds in memory, used
 * by DenseTensorAttribute.
 *
 * Tensor dimension size information for unbound dimensions is at
 * negative offset to preserve cell array aligment without
 * introducing excessive padding, e.g. if tensor store is setup for
 * tensors of type tensor(x[]) then a tensor of type tensor(x[3]) will
 * use 32 bytes (inclusive 4 bytes padding).
 *
 * If both start of tensor dimension size information and start of
 * tensor cells were to be 32 byte aligned then tensors of type tensor(x[3])
 * would use 64 bytes.
 */
class DenseTensorStore : public TensorStore
{
public:
    // 32 entry alignment, entry type is char => 32 bytes alignment
    using RefType = datastore::AlignedEntryRefT<22, 5>;
    using DataStoreType = datastore::DataStoreT<RefType>;
    using ValueType = vespalib::eval::ValueType;

    class BufferType : public datastore::BufferType<char>
    {
        using CleanContext = datastore::BufferType<char>::CleanContext;
        uint32_t _unboundDimSizesSize;
    public:
        BufferType();
        ~BufferType() override;
        void cleanHold(void *buffer, uint64_t offset, uint64_t len, CleanContext cleanCtx) override;
        uint32_t unboundDimSizesSize() const { return _unboundDimSizesSize; }
        void setUnboundDimSizesSize(uint32_t unboundDimSizesSize_in) {
            _unboundDimSizesSize = unboundDimSizesSize_in;
        }
        size_t getReservedElements(uint32_t bufferId) const override;
    };
private:
    DataStoreType _concreteStore;
    BufferType _bufferType;
    ValueType _type; // type of dense tensor
    size_t _numBoundCells; // product of bound dimension sizes
    uint32_t _numUnboundDims;
    uint32_t _cellSize; // size of a cell (e.g. double => 8)
    std::vector<double> _emptyCells;

    size_t unboundCells(const void *buffer) const;

    template <class TensorType>
    TensorStore::EntryRef
    setDenseTensor(const TensorType &tensor);
    datastore::Handle<char> allocRawBuffer(size_t numCells);
    size_t alignedSize(size_t numCells) const {
        return RefType::align(numCells * _cellSize + unboundDimSizesSize());
    }

public:
    DenseTensorStore(const ValueType &type);
    ~DenseTensorStore() override;

    const ValueType &type() const { return _type; }
    uint32_t unboundDimSizesSize() const { return _bufferType.unboundDimSizesSize(); }
    size_t getNumCells(const void *buffer) const;
    uint32_t getCellSize() const { return _cellSize; }
    const void *getRawBuffer(RefType ref) const;
    datastore::Handle<char> allocRawBuffer(size_t numCells, const std::vector<uint32_t> &unboundDimSizes);
    void holdTensor(EntryRef ref) override;
    EntryRef move(EntryRef ref) override;
    std::unique_ptr<Tensor> getTensor(EntryRef ref) const;
    void getTensor(EntryRef ref, vespalib::tensor::MutableDenseTensorView &tensor) const;
    EntryRef setTensor(const Tensor &tensor);
};

}
