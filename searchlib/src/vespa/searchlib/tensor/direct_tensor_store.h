// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_store.h"
#include <memory>

namespace vespalib::eval { struct Value; }

namespace search::tensor {

/**
 * Class for storing heap allocated tensors, referenced by EntryRefs.
 *
 * Shared pointers to the tensors are stored in an underlying data store.
 */
class DirectTensorStore : public TensorStore {
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
        virtual void cleanHold(void* buffer, size_t offset, ElemCount num_elems, CleanContext clean_ctx) override;
    };

    TensorStoreType _tensor_store;

    EntryRef add_entry(TensorSP tensor);

public:
    DirectTensorStore();
    ~DirectTensorStore() override;
    using RefType = TensorStoreType::RefType;

    const vespalib::eval::Value * get_tensor(EntryRef ref) const;
    EntryRef store_tensor(std::unique_ptr<vespalib::eval::Value> tensor);

    void holdTensor(EntryRef ref) override;
    EntryRef move(EntryRef ref) override;
};

}
