// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_buffer_store.h"
#include <vespa/document/util/serializableexceptions.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/streamed/streamed_value_builder_factory.h>
#include <vespa/vespalib/datastore/array_store.hpp>
#include <vespa/vespalib/datastore/buffer_type.hpp>
#include <vespa/vespalib/datastore/compaction_context.h>
#include <vespa/vespalib/datastore/compaction_strategy.h>
#include <vespa/vespalib/datastore/datastore.hpp>
#include <vespa/vespalib/util/size_literals.h>

using document::DeserializeException;
using vespalib::alloc::MemoryAllocator;
using vespalib::datastore::CompactionContext;
using vespalib::datastore::CompactionStrategy;
using vespalib::datastore::EntryRef;
using vespalib::eval::StreamedValueBuilderFactory;
using vespalib::eval::Value;
using vespalib::eval::ValueType;

namespace search::tensor {

namespace {

constexpr float ALLOC_GROW_FACTOR = 0.2;

constexpr double mapper_grow_factor = 1.02;

}

TensorBufferStore::TensorBufferStore(const ValueType& tensor_type, std::shared_ptr<MemoryAllocator> allocator, uint32_t max_small_subspaces_type_id)
    : TensorStore(ArrayStoreType::get_data_store_base(_array_store)),
      _tensor_type(tensor_type),
      _ops(_tensor_type),
      _array_store(ArrayStoreType::optimizedConfigForHugePage(max_small_subspaces_type_id,
                                                              TensorBufferTypeMapper(max_small_subspaces_type_id, mapper_grow_factor, &_ops),
                                                              MemoryAllocator::HUGEPAGE_SIZE,
                                                              MemoryAllocator::PAGE_SIZE,
                                                              8_Ki, ALLOC_GROW_FACTOR),
                   std::move(allocator), TensorBufferTypeMapper(max_small_subspaces_type_id, mapper_grow_factor, &_ops))
{
}

TensorBufferStore::~TensorBufferStore() = default;

void
TensorBufferStore::holdTensor(EntryRef ref)
{
    _array_store.remove(ref);
}

EntryRef
TensorBufferStore::move_on_compact(EntryRef ref)
{
    if (!ref.valid()) {
        return EntryRef();
    }
    auto buf = _array_store.get_writable(ref);
    auto new_ref = _array_store.add(buf);
    _ops.copied_labels(buf);
    return new_ref;
}

vespalib::MemoryUsage
TensorBufferStore::update_stat(const CompactionStrategy& compaction_strategy)
{
    auto array_store_address_space_usage = _store.getAddressSpaceUsage();
    auto array_store_memory_usage = _store.getMemoryUsage();
    _compaction_spec = compaction_strategy.should_compact(array_store_memory_usage, array_store_address_space_usage);
    return array_store_memory_usage;
}

std::unique_ptr<vespalib::datastore::ICompactionContext>
TensorBufferStore::start_compact(const CompactionStrategy& compaction_strategy)
{
    auto compacting_buffers = _store.start_compact_worst_buffers(_compaction_spec, compaction_strategy);
    return std::make_unique<CompactionContext>(*this, std::move(compacting_buffers));
}

EntryRef
TensorBufferStore::store_tensor(const Value &tensor)
{
    uint32_t num_subspaces = tensor.index().size();
    auto buffer_size = _ops.get_buffer_size(num_subspaces);
    auto& mapper = _array_store.get_mapper();
    auto type_id = mapper.get_type_id(buffer_size);
    auto array_size = (type_id != 0) ? mapper.get_array_size(type_id) : buffer_size;
    assert(array_size >= buffer_size);
    auto ref = _array_store.allocate(array_size);
    auto buf = _array_store.get_writable(ref);
    _ops.store_tensor(buf, tensor);
    return ref;
}

EntryRef
TensorBufferStore::store_encoded_tensor(vespalib::nbostream &encoded)
{
    const auto &factory = StreamedValueBuilderFactory::get();
    auto val = vespalib::eval::decode_value(encoded, factory);
    if (!encoded.empty()) {
        throw DeserializeException("Leftover bytes deserializing tensor attribute value.", VESPA_STRLOC);
    }
    return store_tensor(*val);
}

std::unique_ptr<Value>
TensorBufferStore::get_tensor(EntryRef ref) const
{
    if (!ref.valid()) {
        return {};
    }
    auto buf = _array_store.get(ref);
    return _ops.make_fast_view(buf, _tensor_type);
}

bool
TensorBufferStore::encode_stored_tensor(EntryRef ref, vespalib::nbostream &target) const
{
    if (!ref.valid()) {
        return false;
    }
    auto buf = _array_store.get(ref);
    _ops.encode_stored_tensor(buf, _tensor_type, target);
    return true;
}

}
