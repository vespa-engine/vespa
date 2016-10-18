// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/btree/entryref.h>
#include <vespa/searchlib/btree/datastore.h>
#include <vespa/vespalib/util/generationhandler.h>

namespace vespalib { namespace tensor { class Tensor; } }

namespace search {

namespace attribute {

/**
 * Class for storing serialized tensors in memory, used by TensorAttribute.
 *
 * Serialization format is subject to change.  Changes to serialization format
 * might also require corresponding changes to implemented optimized tensor
 * operations that use the serialized tensor as argument.
 */
class TensorStore
{
public:
    using RefType = btree::AlignedEntryRefT<22, 2>;
    using DataStoreType = btree::DataStoreT<RefType>;
    typedef vespalib::GenerationHandler::generation_t generation_t;
    using Tensor = vespalib::tensor::Tensor;

private:
    DataStoreType _store;
    btree::BufferType<char> _type;
    const uint32_t          _typeId;

public:
    TensorStore();

    ~TensorStore();

    // Inherit doc from DataStoreBase
    void
    trimHoldLists(generation_t usedGen)
    {
        _store.trimHoldLists(usedGen);
    }

    // Inherit doc from DataStoreBase
    void
    transferHoldLists(generation_t generation)
    {
        _store.transferHoldLists(generation);
    }

    void
    clearHoldLists(void)
    {
        _store.clearHoldLists();
    }

    MemoryUsage
    getMemoryUsage() const
    {
        return _store.getMemoryUsage();
    }


    std::pair<const void *, uint32_t> getRawBuffer(RefType ref) const;

    std::pair<void *, RefType> allocRawBuffer(uint32_t size);

    void hold(RefType ref);

    RefType move(RefType ref);

    std::unique_ptr<Tensor> getTensor(RefType ref) const;

    void holdTensor(RefType ref) { hold(ref); }

    RefType setTensor(const Tensor &tensor);

    uint32_t startCompactWorstBuffer() {
        return _store.startCompactWorstBuffer(_typeId);
    }

    void finishCompactWorstBuffer(uint32_t bufferId) {
        _store.holdBuffer(bufferId);
    }
};


}  // namespace search::attribute

}  // namespace search
