// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_store.h"
#include "subspace_type.h"
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/datastore/compacting_buffers.h>
#include <vespa/vespalib/datastore/compaction_context.h>
#include <vespa/vespalib/datastore/compaction_strategy.h>
#include <vespa/vespalib/datastore/datastore.hpp>
#include <vespa/vespalib/util/memory_allocator.h>

using vespalib::datastore::CompactionContext;
using vespalib::datastore::CompactionSpec;
using vespalib::datastore::CompactionStrategy;
using vespalib::datastore::EntryRef;
using vespalib::datastore::Handle;
using vespalib::datastore::ICompactionContext;
using vespalib::eval::CellType;
using vespalib::eval::CellTypeUtils;
using vespalib::eval::Value;
using vespalib::eval::ValueType;

namespace search::tensor {

namespace {

constexpr size_t MIN_BUFFER_ARRAYS = 1024;
constexpr size_t DENSE_TENSOR_ALIGNMENT = 32;
constexpr size_t DENSE_TENSOR_ALIGNMENT_SMALL = 16;
constexpr size_t DENSE_TENSOR_ALIGNMENT_MIN = 8;

size_t my_align(size_t size, size_t alignment) {
    size += alignment - 1;
    return (size - (size % alignment));
}

}

DenseTensorStore::TensorSizeCalc::TensorSizeCalc(const ValueType &type)
    : _numCells(1u),
      _cell_type(type.cell_type()),
      _aligned_size(0u)
{
    for (const auto &dim: type.dimensions()) {
        _numCells *= dim.size;
    }
    auto buf_size = bufSize();
    size_t alignment = DENSE_TENSOR_ALIGNMENT;
    if (buf_size <= DENSE_TENSOR_ALIGNMENT_MIN) {
        alignment = DENSE_TENSOR_ALIGNMENT_MIN;
    } else if (buf_size <= DENSE_TENSOR_ALIGNMENT_SMALL) {
        alignment = DENSE_TENSOR_ALIGNMENT_SMALL;
    }
    _aligned_size = my_align(buf_size, alignment);
}

DenseTensorStore::BufferType::BufferType(const TensorSizeCalc &tensorSizeCalc, std::shared_ptr<vespalib::alloc::MemoryAllocator> allocator)
    : vespalib::datastore::BufferType<char>(tensorSizeCalc.alignedSize(), MIN_BUFFER_ARRAYS, RefType::offsetSize()),
      _allocator(std::move(allocator))
{}

DenseTensorStore::BufferType::~BufferType() = default;

void
DenseTensorStore::BufferType::clean_hold(void *buffer, size_t offset, EntryCount num_entries, CleanContext)
{
    auto num_elems = num_entries * getArraySize();
    memset(static_cast<char *>(buffer) + offset * getArraySize(), 0, num_elems);
}

const vespalib::alloc::MemoryAllocator*
DenseTensorStore::BufferType::get_memory_allocator() const
{
    return _allocator.get();
}

DenseTensorStore::DenseTensorStore(const ValueType &type, std::shared_ptr<vespalib::alloc::MemoryAllocator> allocator)
    : TensorStore(_concreteStore),
      _concreteStore(),
      _tensorSizeCalc(type),
      _bufferType(_tensorSizeCalc, std::move(allocator)),
      _type(type),
      _subspace_type(type),
      _empty(_subspace_type)
{
    _store.addType(&_bufferType);
    _store.init_primary_buffers();
    _store.enableFreeLists();
}

DenseTensorStore::~DenseTensorStore()
{
    _store.dropBuffers();
}

namespace {

void clearPadAreaAfterBuffer(char *buffer, size_t bufSize, size_t alignedBufSize) {
    size_t padSize = alignedBufSize - bufSize;
    memset(buffer + bufSize, 0, padSize);
}

}

Handle<char>
DenseTensorStore::allocRawBuffer()
{
    size_t bufSize = getBufSize();
    size_t alignedBufSize = _tensorSizeCalc.alignedSize();
    auto result = _concreteStore.freeListRawAllocator<char>(0u).alloc(1);
    clearPadAreaAfterBuffer(result.data, bufSize, alignedBufSize);
    return result;
}

void
DenseTensorStore::holdTensor(EntryRef ref)
{
    if (!ref.valid()) {
        return;
    }
    _concreteStore.hold_entry(ref);
}

TensorStore::EntryRef
DenseTensorStore::move_on_compact(EntryRef ref)
{
    if (!ref.valid()) {
        return RefType();
    }
    auto oldraw = getRawBuffer(ref);
    auto newraw = allocRawBuffer();
    memcpy(newraw.data, static_cast<const char *>(oldraw), getBufSize());
    return newraw.ref;
}

vespalib::MemoryUsage
DenseTensorStore::update_stat(const CompactionStrategy& compaction_strategy)
{
    auto memory_usage = _store.getMemoryUsage();
    _compaction_spec = CompactionSpec(compaction_strategy.should_compact_memory(memory_usage), false);
    return memory_usage;
}

std::unique_ptr<ICompactionContext>
DenseTensorStore::start_compact(const CompactionStrategy& compaction_strategy)
{
    auto compacting_buffers = _store.start_compact_worst_buffers(_compaction_spec, compaction_strategy);
    return std::make_unique<CompactionContext>(*this, std::move(compacting_buffers));
}

EntryRef
DenseTensorStore::store_tensor(const Value& tensor)
{
    assert(tensor.type() == _type);
    auto cells = tensor.cells();
    assert(cells.size == getNumCells());
    assert(cells.type == _type.cell_type());
    auto raw = allocRawBuffer();
    memcpy(raw.data, cells.data, getBufSize());
    return raw.ref;
}

EntryRef
DenseTensorStore::store_encoded_tensor(vespalib::nbostream& encoded)
{
    (void) encoded;
    abort();
}

std::unique_ptr<Value>
DenseTensorStore::get_tensor(EntryRef ref) const
{
    if (!ref.valid()) {
        return {};
    }
    vespalib::eval::TypedCells cells_ref(getRawBuffer(ref), _type.cell_type(), getNumCells());
    return std::make_unique<vespalib::eval::DenseValueView>(_type, cells_ref);
}

bool
DenseTensorStore::encode_stored_tensor(EntryRef ref, vespalib::nbostream& target) const
{
    (void) ref;
    (void) target;
    abort();
}

const DenseTensorStore*
DenseTensorStore::as_dense() const
{
    return this;
}

DenseTensorStore*
DenseTensorStore::as_dense()
{
    return this;
}

}
