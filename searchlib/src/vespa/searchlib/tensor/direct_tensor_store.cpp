// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "direct_tensor_store.h"
#include <vespa/eval/tensor/tensor.h>
#include <vespa/vespalib/datastore/datastore.hpp>

using vespalib::datastore::EntryRef;

namespace search::tensor {

constexpr size_t MIN_BUFFER_ARRAYS = 8192;

DirectTensorStore::TensorBufferType::TensorBufferType()
    : ParentType(1, MIN_BUFFER_ARRAYS, TensorStoreType::RefType::offsetSize())
{
}

void
DirectTensorStore::TensorBufferType::cleanHold(void* buffer, size_t offset, size_t num_elems, CleanContext clean_ctx)
{
    TensorSP* elem = static_cast<TensorSP*>(buffer) + offset;
    for (size_t i = 0; i < num_elems; ++i) {
        clean_ctx.extraBytesCleaned((*elem)->count_memory_used());
        *elem = _emptyEntry;
        ++elem;
    }
}

EntryRef
DirectTensorStore::add_entry(TensorSP tensor)
{
    auto ref = _tensor_store.addEntry(tensor);
    auto& state = _tensor_store.getBufferState(RefType(ref).bufferId());
    state.incExtraUsedBytes(tensor->count_memory_used());
    return ref;
}

DirectTensorStore::DirectTensorStore()
    : TensorStore(_tensor_store),
      _tensor_store(std::make_unique<TensorBufferType>())
{
    _tensor_store.enableFreeLists();
}

const vespalib::tensor::Tensor*
DirectTensorStore::get_tensor(EntryRef ref) const
{
    if (!ref.valid()) {
        return nullptr;
    }
    const auto& entry = _tensor_store.getEntry(ref);
    assert(entry);
    return entry.get();
}

EntryRef
DirectTensorStore::store_tensor(std::unique_ptr<Tensor> tensor)
{
    assert(tensor);
    return add_entry(TensorSP(tensor.release()));
}

void
DirectTensorStore::holdTensor(EntryRef ref)
{
    if (!ref.valid()) {
        return;
    }
    const auto& tensor = _tensor_store.getEntry(ref);
    assert(tensor);
    _tensor_store.holdElem(ref, 1, tensor->count_memory_used());
}

EntryRef
DirectTensorStore::move(EntryRef ref)
{
    if (!ref.valid()) {
        return EntryRef();
    }
    const auto& old_tensor = _tensor_store.getEntry(ref);
    assert(old_tensor);
    auto new_ref = add_entry(old_tensor);
    _tensor_store.holdElem(ref, 1, old_tensor->count_memory_used());
    return new_ref;
}

}
