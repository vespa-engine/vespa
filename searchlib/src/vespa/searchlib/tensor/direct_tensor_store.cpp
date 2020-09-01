// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "direct_tensor_store.h"
#include <vespa/eval/tensor/tensor.h>
#include <vespa/vespalib/datastore/datastore.hpp>

using vespalib::datastore::EntryRef;

namespace search::tensor {

constexpr size_t MIN_BUFFER_ARRAYS = 8192;

DirectTensorStore::DirectTensorStore()
    : TensorStore(_concrete_store),
      _concrete_store(MIN_BUFFER_ARRAYS)
{
}

const vespalib::tensor::Tensor*
DirectTensorStore::get_tensor(EntryRef ref) const
{
    if (!ref.valid()) {
        return nullptr;
    }
    auto entry = _concrete_store.getEntry(ref);
    assert(entry);
    return entry.get();
}

EntryRef
DirectTensorStore::store_tensor(std::unique_ptr<Tensor> tensor)
{
    assert(tensor);
    // TODO: Account for heap allocated memory
    return _concrete_store.addEntry(TensorSP(tensor.release()));
}

void
DirectTensorStore::holdTensor(EntryRef ref)
{
    if (!ref.valid()) {
        return;
    }
    // TODO: Account for heap allocated memory
    _concrete_store.holdElem(ref, 1);
}

EntryRef
DirectTensorStore::move(EntryRef ref)
{
    if (!ref.valid()) {
        return EntryRef();
    }
    auto old_tensor = _concrete_store.getEntry(ref);
    assert(old_tensor);
    // TODO: Account for heap allocated memory (regular + hold)
    auto new_ref = _concrete_store.addEntry(old_tensor);
    _concrete_store.holdElem(ref, 1);
    return new_ref;
}

}
