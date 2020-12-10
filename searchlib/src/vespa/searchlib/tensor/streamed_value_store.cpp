// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "streamed_value_store.h"
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/streamed/streamed_value_builder_factory.h>
#include <vespa/eval/streamed/streamed_value_view.h>
#include <vespa/vespalib/datastore/datastore.hpp>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/log/log.h>

LOG_SETUP(".searchlib.tensor.streamed_value_store");

using vespalib::datastore::Handle;
using vespalib::datastore::EntryRef;
using namespace vespalib::eval;

namespace search::tensor {

constexpr size_t MIN_BUFFER_ARRAYS = 8192;

StreamedValueStore::TensorBufferType::TensorBufferType()
    : ParentType(1, MIN_BUFFER_ARRAYS, TensorStoreType::RefType::offsetSize())
{
}

void
StreamedValueStore::TensorBufferType::cleanHold(void* buffer, size_t offset, size_t num_elems, CleanContext clean_ctx)
{
    TensorSP* elem = static_cast<TensorSP*>(buffer) + offset;
    for (size_t i = 0; i < num_elems; ++i) {
        clean_ctx.extraBytesCleaned((*elem)->get_memory_usage().allocatedBytes());
        *elem = _emptyEntry;
        ++elem;
    }
}

StreamedValueStore::StreamedValueStore(const ValueType &tensor_type)
  : TensorStore(_concrete_store),
    _concrete_store(),
    _tensor_type(tensor_type)
{
    _concrete_store.enableFreeLists();
}

StreamedValueStore::~StreamedValueStore() = default;

EntryRef
StreamedValueStore::add_entry(TensorSP tensor)
{
    auto ref = _concrete_store.addEntry(tensor);
    auto& state = _concrete_store.getBufferState(RefType(ref).bufferId());
    state.incExtraUsedBytes(tensor->get_memory_usage().allocatedBytes());
    return ref;
}

const vespalib::eval::Value *
StreamedValueStore::get_tensor(EntryRef ref) const
{
    if (!ref.valid()) {
        return nullptr;
    }
    const auto& entry = _concrete_store.getEntry(ref);
    assert(entry);
    return entry.get();
}

void
StreamedValueStore::holdTensor(EntryRef ref)
{
    if (!ref.valid()) {
        return;
    }
    const auto& tensor = _concrete_store.getEntry(ref);
    assert(tensor);
    _concrete_store.holdElem(ref, 1, tensor->get_memory_usage().allocatedBytes());
}

TensorStore::EntryRef
StreamedValueStore::move(EntryRef ref)
{
    if (!ref.valid()) {
        return EntryRef();
    }
    const auto& old_tensor = _concrete_store.getEntry(ref);
    assert(old_tensor);
    auto new_ref = add_entry(old_tensor);
    _concrete_store.holdElem(ref, 1, old_tensor->get_memory_usage().allocatedBytes());
    return new_ref;
}

bool
StreamedValueStore::encode_tensor(EntryRef ref, vespalib::nbostream &target) const
{
    if (const auto * val = get_tensor(ref)) {
        vespalib::eval::encode_value(*val, target);
        return true;
    } else {
        return false;
    }
}

TensorStore::EntryRef
StreamedValueStore::store_tensor(const Value &tensor)
{
    assert(tensor.type() == _tensor_type);
    auto val = StreamedValueBuilderFactory::get().copy(tensor);
    return add_entry(TensorSP(std::move(val)));
}

TensorStore::EntryRef
StreamedValueStore::store_encoded_tensor(vespalib::nbostream &encoded)
{
    const auto &factory = StreamedValueBuilderFactory::get();
    auto val = vespalib::eval::decode_value(encoded, factory);
    return add_entry(TensorSP(std::move(val)));
}

}
