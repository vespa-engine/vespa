// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/entryref.h>
#include <vespa/vespalib/datastore/datastore.h>
#include <vespa/vespalib/util/generationhandler.h>

namespace vespalib::eval { struct Value; }

namespace search::tensor {

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
    using EntryRef = vespalib::datastore::EntryRef;
    typedef vespalib::GenerationHandler::generation_t generation_t;

protected:
    vespalib::datastore::DataStoreBase &_store;
    const uint32_t        _typeId;

public:
    TensorStore(vespalib::datastore::DataStoreBase &store);

    virtual ~TensorStore();

    virtual void holdTensor(EntryRef ref) = 0;
    virtual EntryRef move(EntryRef ref) = 0;

    // Inherit doc from DataStoreBase
    void trimHoldLists(generation_t usedGen) {
        _store.trimHoldLists(usedGen);
    }

    // Inherit doc from DataStoreBase
    void transferHoldLists(generation_t generation) {
        _store.transferHoldLists(generation);
    }

    void clearHoldLists() {
        _store.clearHoldLists();
    }

    vespalib::MemoryUsage getMemoryUsage() const {
        return _store.getMemoryUsage();
    }

    uint32_t startCompactWorstBuffer() {
        return _store.startCompactWorstBuffer(_typeId);
    }

    void finishCompactWorstBuffer(uint32_t bufferId) {
        _store.holdBuffer(bufferId);
    }
};

}
