// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_store.h"
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/streamed/streamed_value.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/typify.h>

namespace search::tensor {

/**
 * Class for StreamedValue tensors in memory.
 */
class StreamedValueStore : public TensorStore {
private:
    // Note: Must use SP (instead of UP) because of fallbackCopy() and initializeReservedElements() in BufferType,
    //       and implementation of move().
    using TensorSP = std::shared_ptr<vespalib::eval::Value>;
    using TensorStoreType = vespalib::datastore::DataStore<TensorSP>;

    class TensorBufferType : public vespalib::datastore::BufferType<TensorSP> {
    private:
        using ParentType = BufferType<TensorSP>;
        using ParentType::_emptyEntry;
        using CleanContext = typename ParentType::CleanContext;
    public:
        TensorBufferType();
        virtual void cleanHold(void* buffer, size_t offset, size_t num_elems, CleanContext clean_ctx) override;
    };
    TensorStoreType _concrete_store;
    const vespalib::eval::ValueType _tensor_type;
    EntryRef add_entry(TensorSP tensor);
public:
    StreamedValueStore(const vespalib::eval::ValueType &tensor_type);
    ~StreamedValueStore() override;

    using RefType = TensorStoreType::RefType;

    void holdTensor(EntryRef ref) override;
    EntryRef move(EntryRef ref) override;

    const vespalib::eval::Value * get_tensor(EntryRef ref) const;
    bool encode_tensor(EntryRef ref, vespalib::nbostream &target) const;

    EntryRef store_tensor(const vespalib::eval::Value &tensor);
    EntryRef store_encoded_tensor(vespalib::nbostream &encoded);
};


}
