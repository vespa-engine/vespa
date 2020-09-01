// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_store.h"
#include <memory>

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
    using TensorSP = std::shared_ptr<Tensor>;
    using DataStoreType = vespalib::datastore::DataStore<TensorSP>;

    DataStoreType _concrete_store;

public:
    DirectTensorStore();
    using RefType = DataStoreType::RefType;

    const Tensor* get_tensor(EntryRef ref) const;
    EntryRef store_tensor(std::unique_ptr<Tensor> tensor);

    void holdTensor(EntryRef ref) override;
    EntryRef move(EntryRef ref) override;
};

}
