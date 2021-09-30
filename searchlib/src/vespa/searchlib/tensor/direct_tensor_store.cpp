// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "direct_tensor_store.h"
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/datastore/datastore.hpp>
#include <vespa/vespalib/datastore/buffer_type.hpp>
#include <vespa/vespalib/util/size_literals.h>

using vespalib::datastore::EntryRef;

namespace search::tensor {

constexpr size_t MIN_BUFFER_ARRAYS = 8_Ki;

DirectTensorStore::TensorBufferType::TensorBufferType()
    : ParentType(1, MIN_BUFFER_ARRAYS, TensorStoreType::RefType::offsetSize())
{
}

void
DirectTensorStore::TensorBufferType::cleanHold(void* buffer, size_t offset, ElemCount num_elems, CleanContext clean_ctx)
{
    TensorSP* elem = static_cast<TensorSP*>(buffer) + offset;
    for (size_t i = 0; i < num_elems; ++i) {
        clean_ctx.extraBytesCleaned((*elem)->get_memory_usage().allocatedBytes());
        *elem = _emptyEntry;
        ++elem;
    }
}

EntryRef
DirectTensorStore::add_entry(TensorSP tensor)
{
    auto ref = _tensor_store.addEntry(tensor);
    auto& state = _tensor_store.getBufferState(RefType(ref).bufferId());
    state.incExtraUsedBytes(tensor->get_memory_usage().allocatedBytes());
    return ref;
}

DirectTensorStore::DirectTensorStore()
    : TensorStore(_tensor_store),
      _tensor_store(std::make_unique<TensorBufferType>())
{
    _tensor_store.enableFreeLists();
}

DirectTensorStore::~DirectTensorStore() = default;

const vespalib::eval::Value *
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
DirectTensorStore::store_tensor(std::unique_ptr<vespalib::eval::Value> tensor)
{
    assert(tensor);
    return add_entry(TensorSP(std::move(tensor)));
}

void
DirectTensorStore::holdTensor(EntryRef ref)
{
    if (!ref.valid()) {
        return;
    }
    const auto& tensor = _tensor_store.getEntry(ref);
    assert(tensor);
    _tensor_store.holdElem(ref, 1, tensor->get_memory_usage().allocatedBytes());
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
    _tensor_store.holdElem(ref, 1, old_tensor->get_memory_usage().allocatedBytes());
    return new_ref;
}

}
