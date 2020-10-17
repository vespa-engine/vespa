// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_store.h"
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/typed_cells.h>

namespace vespalib { namespace tensor { class MutableDenseTensorView; }}
namespace vespalib::eval { struct Value; }

namespace search::tensor {

/**
 * Class for storing dense tensors with known bounds in memory, used
 * by DenseTensorAttribute.
 */
class DenseTensorStore : public TensorStore
{
public:
    using RefType = vespalib::datastore::EntryRefT<22>;
    using DataStoreType = vespalib::datastore::DataStoreT<RefType>;
    using ValueType = vespalib::eval::ValueType;

    struct TensorSizeCalc
    {
        size_t   _numCells; // product of dimension sizes
        uint32_t _cellSize; // size of a cell (e.g. double => 8, float => 4)

        TensorSizeCalc(const ValueType &type);
        size_t bufSize() const { return (_numCells * _cellSize); }
        size_t alignedSize() const;
    };

    class BufferType : public vespalib::datastore::BufferType<char>
    {
        using CleanContext = vespalib::datastore::BufferType<char>::CleanContext;
    public:
        BufferType(const TensorSizeCalc &tensorSizeCalc);
        ~BufferType() override;
        void cleanHold(void *buffer, size_t offset, size_t numElems, CleanContext cleanCtx) override;
    };
private:
    DataStoreType _concreteStore;
    TensorSizeCalc _tensorSizeCalc;
    BufferType _bufferType;
    ValueType _type; // type of dense tensor
    std::vector<char> _emptySpace;

    size_t unboundCells(const void *buffer) const;

    template <class TensorType>
    TensorStore::EntryRef
    setDenseTensor(const TensorType &tensor);

public:
    DenseTensorStore(const ValueType &type);
    ~DenseTensorStore() override;

    const ValueType &type() const { return _type; }
    size_t getNumCells() const { return _tensorSizeCalc._numCells; }
    uint32_t getCellSize() const { return _tensorSizeCalc._cellSize; }
    size_t getBufSize() const { return _tensorSizeCalc.bufSize(); }
    const void *getRawBuffer(RefType ref) const;
    vespalib::datastore::Handle<char> allocRawBuffer();
    void holdTensor(EntryRef ref) override;
    EntryRef move(EntryRef ref) override;
    std::unique_ptr<vespalib::eval::Value> getTensor(EntryRef ref) const;
    void getTensor(EntryRef ref, vespalib::tensor::MutableDenseTensorView &tensor) const;
    vespalib::eval::TypedCells get_typed_cells(EntryRef ref) const;
    EntryRef setTensor(const vespalib::eval::Value &tensor);
    // The following method is meant to be used only for unit tests.
    uint32_t getArraySize() const { return _bufferType.getArraySize(); }
};

}
