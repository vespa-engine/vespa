// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/datastore/entryref.h>
#include <vespa/searchlib/datastore/datastore.h>
#include <vespa/vespalib/util/generationhandler.h>

namespace vespalib { namespace tensor { struct Tensor; } }

namespace search {

namespace tensor {

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
    using EntryRef = datastore::EntryRef;
    typedef vespalib::GenerationHandler::generation_t generation_t;
    using Tensor = vespalib::tensor::Tensor;

protected:
    datastore::DataStoreBase &_store;
    const uint32_t        _typeId;

public:
    TensorStore(datastore::DataStoreBase &store);

    virtual ~TensorStore();

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
    clearHoldLists()
    {
        _store.clearHoldLists();
    }

    MemoryUsage
    getMemoryUsage() const
    {
        return _store.getMemoryUsage();
    }


    virtual void holdTensor(EntryRef ref) = 0;

    virtual EntryRef move(EntryRef ref) = 0;

    uint32_t startCompactWorstBuffer() {
        return _store.startCompactWorstBuffer(_typeId);
    }

    void finishCompactWorstBuffer(uint32_t bufferId) {
        _store.holdBuffer(bufferId);
    }
};


}  // namespace search::tensor

}  // namespace search
