// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_store.h"
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/datastore/datastore.hpp>
#include <vespa/vespalib/util/memory_allocator.h>

using vespalib::datastore::Handle;
using vespalib::eval::CellType;
using vespalib::eval::CellTypeUtils;
using vespalib::eval::Value;
using vespalib::eval::ValueType;

namespace search::tensor {

namespace {

constexpr size_t MIN_BUFFER_ARRAYS = 1024;
constexpr size_t DENSE_TENSOR_ALIGNMENT = 32;

size_t my_align(size_t size, size_t alignment) {
    size += alignment - 1;
    return (size - (size % alignment));
}

}

DenseTensorStore::TensorSizeCalc::TensorSizeCalc(const ValueType &type)
    : _numCells(1u),
      _cell_type(type.cell_type())
{
    for (const auto &dim: type.dimensions()) {
        _numCells *= dim.size;
    }
}

size_t
DenseTensorStore::TensorSizeCalc::alignedSize() const
{
    return my_align(bufSize(), DENSE_TENSOR_ALIGNMENT);
}

DenseTensorStore::BufferType::BufferType(const TensorSizeCalc &tensorSizeCalc, std::unique_ptr<vespalib::alloc::MemoryAllocator> allocator)
    : vespalib::datastore::BufferType<char>(tensorSizeCalc.alignedSize(), MIN_BUFFER_ARRAYS, RefType::offsetSize()),
      _allocator(std::move(allocator))
{}

DenseTensorStore::BufferType::~BufferType() = default;

void
DenseTensorStore::BufferType::cleanHold(void *buffer, size_t offset,
                                        ElemCount numElems, CleanContext)
{
    memset(static_cast<char *>(buffer) + offset, 0, numElems);
}

const vespalib::alloc::MemoryAllocator*
DenseTensorStore::BufferType::get_memory_allocator() const
{
    return _allocator.get();
}

DenseTensorStore::DenseTensorStore(const ValueType &type, std::unique_ptr<vespalib::alloc::MemoryAllocator> allocator)
    : TensorStore(_concreteStore),
      _concreteStore(),
      _tensorSizeCalc(type),
      _bufferType(_tensorSizeCalc, std::move(allocator)),
      _type(type),
      _emptySpace()
{
    _emptySpace.resize(getBufSize(), 0);
    _store.addType(&_bufferType);
    _store.init_primary_buffers();
    _store.enableFreeLists();
}

DenseTensorStore::~DenseTensorStore()
{
    _store.dropBuffers();
}

const void *
DenseTensorStore::getRawBuffer(RefType ref) const
{
    return _store.getEntryArray<char>(ref, _bufferType.getArraySize());
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
    auto result = _concreteStore.freeListRawAllocator<char>(_typeId).alloc(alignedBufSize);
    clearPadAreaAfterBuffer(result.data, bufSize, alignedBufSize);
    return result;
}

void
DenseTensorStore::holdTensor(EntryRef ref)
{
    if (!ref.valid()) {
        return;
    }
    _concreteStore.holdElem(ref, _tensorSizeCalc.alignedSize());
}

TensorStore::EntryRef
DenseTensorStore::move(EntryRef ref)
{
    if (!ref.valid()) {
        return RefType();
    }
    auto oldraw = getRawBuffer(ref);
    auto newraw = allocRawBuffer();
    memcpy(newraw.data, static_cast<const char *>(oldraw), getBufSize());
    _concreteStore.holdElem(ref, _tensorSizeCalc.alignedSize());
    return newraw.ref;
}

std::unique_ptr<Value>
DenseTensorStore::getTensor(EntryRef ref) const
{
    if (!ref.valid()) {
        return {};
    }
    vespalib::eval::TypedCells cells_ref(getRawBuffer(ref), _type.cell_type(), getNumCells());
    return std::make_unique<vespalib::eval::DenseValueView>(_type, cells_ref);
}

vespalib::eval::TypedCells
DenseTensorStore::get_typed_cells(EntryRef ref) const
{
    if (!ref.valid()) {
        return vespalib::eval::TypedCells(&_emptySpace[0], _type.cell_type(), getNumCells());
    }
    return vespalib::eval::TypedCells(getRawBuffer(ref), _type.cell_type(), getNumCells());
}

template <class TensorType>
TensorStore::EntryRef
DenseTensorStore::setDenseTensor(const TensorType &tensor)
{
    assert(tensor.type() == _type);
    auto cells = tensor.cells();
    assert(cells.size == getNumCells());
    assert(cells.type == _type.cell_type());
    auto raw = allocRawBuffer();
    memcpy(raw.data, cells.data, getBufSize());
    return raw.ref;
}

TensorStore::EntryRef
DenseTensorStore::setTensor(const vespalib::eval::Value &tensor)
{
    return setDenseTensor(tensor);
}

}
