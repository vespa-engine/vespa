// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_store.h"
#include <vespa/vespalib/eval/value_type.h>

namespace search {

namespace attribute {

/**
 * Class for storing serialized tensors in memory, used by TensorAttribute.
 *
 * Serialization format is subject to change.  Changes to serialization format
 * might also require corresponding changes to implemented optimized tensor
 * operations that use the serialized tensor as argument.
 */
class DenseTensorStore : public TensorStore
{
public:
    // 2 entry alignment, entry type is double => 16 bytes alignment
    using RefType = btree::AlignedEntryRefT<22, 1>;
    using DataStoreType = btree::DataStoreT<RefType>;
    using ValueType = vespalib::eval::ValueType;
private:
    DataStoreType _mystore;
    btree::BufferType<double> _bufferType;
    ValueType _type; // type of dense tensor
    size_t _size; // number of cells in dense tensor

    template <class TensorType>
    TensorStore::EntryRef
    setDenseTensor(const TensorType &tensor);
public:
    DenseTensorStore(const ValueType &type);
    virtual ~DenseTensorStore();

    size_t size() const { return _size; }
    const double *getRawBuffer(RefType ref) const;
    std::pair<double *, RefType> allocRawBuffer();
    virtual void holdTensor(EntryRef ref) override;
    virtual EntryRef move(EntryRef ref) override;
    std::unique_ptr<Tensor> getTensor(EntryRef ref) const;
    EntryRef setTensor(const Tensor &tensor);
};


}  // namespace search::attribute

}  // namespace search
