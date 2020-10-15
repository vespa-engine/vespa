// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_store.h"

namespace vespalib::eval { class Value; }

namespace search::tensor {

/**
 * Class for storing serialized tensors in memory, used by TensorAttribute.
 *
 * Serialization format is subject to change.  Changes to serialization format
 * might also require corresponding changes to implemented optimized tensor
 * operations that use the serialized tensor as argument.
 */
class SerializedTensorStore : public TensorStore {
public:
    using RefType = vespalib::datastore::AlignedEntryRefT<22, 2>;
    using DataStoreType = vespalib::datastore::DataStoreT<RefType>;
private:
    DataStoreType _concreteStore;
    vespalib::datastore::BufferType<char> _bufferType;
public:
    SerializedTensorStore();

    virtual ~SerializedTensorStore();

    std::pair<const void *, uint32_t> getRawBuffer(RefType ref) const;

    vespalib::datastore::Handle<char> allocRawBuffer(uint32_t size);

    virtual void holdTensor(EntryRef ref) override;

    virtual EntryRef move(EntryRef ref) override;

    std::unique_ptr<vespalib::eval::Value> getTensor(EntryRef ref) const;

    EntryRef setTensor(const vespalib::eval::Value &tensor);
};

}
