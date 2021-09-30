// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "streamed_value_store.h"
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/fast_value.hpp>
#include <vespa/eval/streamed/streamed_value_builder_factory.h>
#include <vespa/eval/streamed/streamed_value_view.h>
#include <vespa/vespalib/datastore/buffer_type.hpp>
#include <vespa/vespalib/datastore/datastore.hpp>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/typify.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/log/log.h>

LOG_SETUP(".searchlib.tensor.streamed_value_store");

using vespalib::datastore::Handle;
using vespalib::datastore::EntryRef;
using namespace vespalib::eval;
using vespalib::ConstArrayRef;
using vespalib::MemoryUsage;
using vespalib::string_id;

namespace search::tensor {

//-----------------------------------------------------------------------------

namespace {

template <typename CT, typename F>
void each_subspace(const Value &value, size_t num_mapped, size_t dense_size, F f) {
    size_t subspace;
    std::vector<string_id> addr(num_mapped);
    std::vector<string_id*> refs;
    refs.reserve(addr.size());
    for (string_id &label: addr) {
        refs.push_back(&label);
    }
    auto cells = value.cells().typify<CT>();
    auto view = value.index().create_view({});
    view->lookup({});
    while (view->next_result(refs, subspace)) {
        size_t offset = subspace * dense_size;
        f(ConstArrayRef<string_id>(addr), ConstArrayRef<CT>(cells.begin() + offset, dense_size));
    }
}

using TensorEntry = StreamedValueStore::TensorEntry;

struct CreateTensorEntry {
    template <typename CT>
    static TensorEntry::SP invoke(const Value &value, size_t num_mapped, size_t dense_size) {
        using EntryImpl = StreamedValueStore::TensorEntryImpl<CT>;
        return std::make_shared<EntryImpl>(value, num_mapped, dense_size);
    }
};

struct MyFastValueView final : Value {
    const ValueType &my_type;
    FastValueIndex my_index;
    TypedCells my_cells;
    MyFastValueView(const ValueType &type_ref, const std::vector<string_id> &handle_view, TypedCells cells, size_t num_mapped, size_t num_spaces)
        : my_type(type_ref),
          my_index(num_mapped, handle_view, num_spaces),
          my_cells(cells)
    {
        const std::vector<string_id> &labels = handle_view;
        for (size_t i = 0; i < num_spaces; ++i) {
            ConstArrayRef<string_id> addr(&labels[i * num_mapped], num_mapped);
            my_index.map.add_mapping(FastAddrMap::hash_labels(addr));
        }
        assert(my_index.map.size() == num_spaces);
    }
    const ValueType &type() const override { return my_type; }
    const Value::Index &index() const override { return my_index; }
    TypedCells cells() const override { return my_cells; }
    MemoryUsage get_memory_usage() const override {
        MemoryUsage usage = self_memory_usage<MyFastValueView>();
        usage.merge(my_index.map.estimate_extra_memory_usage());
        return usage;
    }
};

} // <unnamed>

//-----------------------------------------------------------------------------

StreamedValueStore::TensorEntry::~TensorEntry() = default;

StreamedValueStore::TensorEntry::SP
StreamedValueStore::TensorEntry::create_shared_entry(const Value &value)
{
    size_t num_mapped = value.type().count_mapped_dimensions();
    size_t dense_size = value.type().dense_subspace_size();
    return vespalib::typify_invoke<1,TypifyCellType,CreateTensorEntry>(value.type().cell_type(), value, num_mapped, dense_size);
}

template <typename CT>
StreamedValueStore::TensorEntryImpl<CT>::TensorEntryImpl(const Value &value, size_t num_mapped, size_t dense_size)
    : handles(),
      cells()
{
    handles.reserve(num_mapped * value.index().size());
    cells.reserve(dense_size * value.index().size());
    auto store_subspace = [&](auto addr, auto data) {
        for (string_id label: addr) {
            handles.push_back(label);
        }
        for (CT entry: data) {
            cells.push_back(entry);
        }
    };
    each_subspace<CT>(value, num_mapped, dense_size, store_subspace);
}

template <typename CT>
Value::UP
StreamedValueStore::TensorEntryImpl<CT>::create_fast_value_view(const ValueType &type_ref) const
{
    size_t num_mapped = type_ref.count_mapped_dimensions();
    size_t dense_size = type_ref.dense_subspace_size();
    size_t num_spaces = cells.size() / dense_size;
    assert(dense_size * num_spaces == cells.size());
    assert(num_mapped * num_spaces == handles.view().size());
    return std::make_unique<MyFastValueView>(type_ref, handles.view(), TypedCells(cells), num_mapped, num_spaces);
}

template <typename CT>
void
StreamedValueStore::TensorEntryImpl<CT>::encode_value(const ValueType &type, vespalib::nbostream &target) const
{
    size_t num_mapped = type.count_mapped_dimensions();
    size_t dense_size = type.dense_subspace_size();
    size_t num_spaces = cells.size() / dense_size;
    assert(dense_size * num_spaces == cells.size());
    assert(num_mapped * num_spaces == handles.view().size());
    StreamedValueView my_value(type, num_mapped, TypedCells(cells), num_spaces, handles.view());
    ::vespalib::eval::encode_value(my_value, target);
}

template <typename CT>
MemoryUsage
StreamedValueStore::TensorEntryImpl<CT>::get_memory_usage() const
{
    MemoryUsage usage = self_memory_usage<TensorEntryImpl<CT>>();
    usage.merge(vector_extra_memory_usage(handles.view()));
    usage.merge(vector_extra_memory_usage(cells));
    return usage;
}

template <typename CT>
StreamedValueStore::TensorEntryImpl<CT>::~TensorEntryImpl() = default;

//-----------------------------------------------------------------------------

constexpr size_t MIN_BUFFER_ARRAYS = 8_Ki;

StreamedValueStore::TensorBufferType::TensorBufferType() noexcept
    : ParentType(1, MIN_BUFFER_ARRAYS, TensorStoreType::RefType::offsetSize())
{
}

void
StreamedValueStore::TensorBufferType::cleanHold(void* buffer, size_t offset, ElemCount num_elems, CleanContext clean_ctx)
{
    TensorEntry::SP* elem = static_cast<TensorEntry::SP*>(buffer) + offset;
    for (size_t i = 0; i < num_elems; ++i) {
        clean_ctx.extraBytesCleaned((*elem)->get_memory_usage().allocatedBytes());
        *elem = _emptyEntry;
        ++elem;
    }
}

StreamedValueStore::StreamedValueStore(const ValueType &tensor_type)
  : TensorStore(_concrete_store),
    _concrete_store(std::make_unique<TensorBufferType>()),
    _tensor_type(tensor_type)
{
    _concrete_store.enableFreeLists();
}

StreamedValueStore::~StreamedValueStore() = default;

EntryRef
StreamedValueStore::add_entry(TensorEntry::SP tensor)
{
    auto ref = _concrete_store.addEntry(tensor);
    auto& state = _concrete_store.getBufferState(RefType(ref).bufferId());
    state.incExtraUsedBytes(tensor->get_memory_usage().allocatedBytes());
    return ref;
}

const StreamedValueStore::TensorEntry *
StreamedValueStore::get_tensor_entry(EntryRef ref) const
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
    if (const auto * entry = get_tensor_entry(ref)) {
        entry->encode_value(_tensor_type, target);
        return true;
    } else {
        return false;
    }
}

TensorStore::EntryRef
StreamedValueStore::store_tensor(const Value &tensor)
{
    assert(tensor.type() == _tensor_type);
    return add_entry(TensorEntry::create_shared_entry(tensor));
}

TensorStore::EntryRef
StreamedValueStore::store_encoded_tensor(vespalib::nbostream &encoded)
{
    const auto &factory = StreamedValueBuilderFactory::get();
    auto val = vespalib::eval::decode_value(encoded, factory);
    return store_tensor(*val);
}

}
