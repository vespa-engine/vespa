// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_store.h"
#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/dense/dense_tensor_view.h>
#include <vespa/eval/tensor/dense/mutable_dense_tensor_view.h>
#include <vespa/eval/tensor/dense/dense_tensor.h>
#include <vespa/eval/tensor/serialization/typed_binary_format.h>
#include <vespa/vespalib/datastore/datastore.hpp>

using search::datastore::Handle;
using vespalib::tensor::Tensor;
using vespalib::tensor::DenseTensorView;
using vespalib::tensor::MutableDenseTensorView;
using vespalib::eval::ValueType;
using CellType = vespalib::eval::ValueType::CellType;

namespace search::tensor {

namespace {

constexpr size_t MIN_BUFFER_ARRAYS = 1024;
constexpr size_t DENSE_TENSOR_ALIGNMENT = 32;

size_t size_of(CellType type) {
    switch (type) {
    case CellType::DOUBLE: return sizeof(double);
    case CellType::FLOAT: return sizeof(float);
    }
    abort();
}

size_t my_align(size_t size, size_t alignment) {
    size += alignment - 1;
    return (size - (size % alignment));
}

}

DenseTensorStore::TensorSizeCalc::TensorSizeCalc(const ValueType &type)
    : _numCells(1u),
      _cellSize(size_of(type.cell_type()))
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

DenseTensorStore::BufferType::BufferType(const TensorSizeCalc &tensorSizeCalc)
    : datastore::BufferType<char>(tensorSizeCalc.alignedSize(), MIN_BUFFER_ARRAYS, RefType::offsetSize())
{}

DenseTensorStore::BufferType::~BufferType() = default;

void
DenseTensorStore::BufferType::cleanHold(void *buffer, size_t offset,
                                        size_t numElems, CleanContext)
{
    memset(static_cast<char *>(buffer) + offset, 0, numElems);
}

DenseTensorStore::DenseTensorStore(const ValueType &type)
    : TensorStore(_concreteStore),
      _concreteStore(),
      _tensorSizeCalc(type),
      _bufferType(_tensorSizeCalc),
      _type(type),
      _emptySpace()
{
    _emptySpace.resize(getBufSize(), 0);
    _store.addType(&_bufferType);
    _store.initActiveBuffers();
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

std::unique_ptr<Tensor>
DenseTensorStore::getTensor(EntryRef ref) const
{
    if (!ref.valid()) {
        return std::unique_ptr<Tensor>();
    }
    vespalib::tensor::TypedCells cells_ref(getRawBuffer(ref), _type.cell_type(), getNumCells());
    return std::make_unique<DenseTensorView>(_type, cells_ref);
}

void
DenseTensorStore::getTensor(EntryRef ref, MutableDenseTensorView &tensor) const
{
    if (!ref.valid()) {
        vespalib::tensor::TypedCells cells_ref(&_emptySpace[0], _type.cell_type(), getNumCells());
        tensor.setCells(cells_ref);
    } else {
        vespalib::tensor::TypedCells cells_ref(getRawBuffer(ref), _type.cell_type(), getNumCells());
        tensor.setCells(cells_ref);
    }
}

vespalib::tensor::TypedCells
DenseTensorStore::get_typed_cells(EntryRef ref) const
{
    if (!ref.valid()) {
        return vespalib::tensor::TypedCells(&_emptySpace[0], _type.cell_type(), getNumCells());
    }
    return vespalib::tensor::TypedCells(getRawBuffer(ref), _type.cell_type(), getNumCells());
}

template <class TensorType>
TensorStore::EntryRef
DenseTensorStore::setDenseTensor(const TensorType &tensor)
{
    size_t numCells = tensor.cellsRef().size;
    assert(numCells == getNumCells());
    assert(tensor.type() == _type);
    auto raw = allocRawBuffer();
    memcpy(raw.data, tensor.cellsRef().data, getBufSize());
    return raw.ref;
}

TensorStore::EntryRef
DenseTensorStore::setTensor(const Tensor &tensor)
{
    const DenseTensorView &view(dynamic_cast<const DenseTensorView &>(tensor));
    return setDenseTensor(view);
}

}
