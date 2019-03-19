// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_store.h"
#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/dense/dense_tensor_view.h>
#include <vespa/eval/tensor/dense/mutable_dense_tensor_view.h>
#include <vespa/eval/tensor/dense/dense_tensor.h>
#include <vespa/eval/tensor/serialization/typed_binary_format.h>
#include <vespa/searchlib/datastore/datastore.hpp>

using search::datastore::Handle;
using vespalib::tensor::Tensor;
using vespalib::tensor::DenseTensor;
using vespalib::tensor::DenseTensorView;
using vespalib::tensor::MutableDenseTensorView;
using vespalib::eval::ValueType;

namespace search::tensor {

namespace {

constexpr size_t MIN_BUFFER_ARRAYS = 1024;
constexpr size_t DENSE_TENSOR_ALIGNMENT = 32;

}

DenseTensorStore::TensorSizeCalc::TensorSizeCalc(const ValueType &type)
    : _numBoundCells(1u),
      _numUnboundDims(0u),
      _cellSize(sizeof(double))
{
    for (const auto & dim : type.dimensions()) {
        if (dim.is_bound()) {
            _numBoundCells *= dim.size;
        } else {
            ++_numUnboundDims;
        }
    }
}

size_t
DenseTensorStore::TensorSizeCalc::arraySize() const
{
    size_t tensorSize = _numBoundCells * _cellSize + 
                        _numUnboundDims * sizeof(uint32_t);
    return DenseTensorStore::BufferType::align(tensorSize, DENSE_TENSOR_ALIGNMENT);
}

DenseTensorStore::BufferType::BufferType(const TensorSizeCalc &tensorSizeCalc)
    : datastore::BufferType<char>(tensorSizeCalc.arraySize(), MIN_BUFFER_ARRAYS, RefType::offsetSize()),
      _unboundDimSizesSize(tensorSizeCalc._numUnboundDims * sizeof(uint32_t))
{}

DenseTensorStore::BufferType::~BufferType() = default;

void
DenseTensorStore::BufferType::cleanHold(void *buffer, size_t offset,
                                        size_t numElems, CleanContext)
{
    // Clear both tensor dimension size information and cells.
    memset(static_cast<char *>(buffer) + offset - _unboundDimSizesSize, 0, numElems);
}

size_t
DenseTensorStore::BufferType::getReservedElements(uint32_t bufferId) const
{
    return datastore::BufferType<char>::getReservedElements(bufferId) +
        align(_unboundDimSizesSize);
}

DenseTensorStore::DenseTensorStore(const ValueType &type)
    : TensorStore(_concreteStore),
      _concreteStore(),
      _tensorSizeCalc(type),
      _bufferType(_tensorSizeCalc),
      _type(type),
      _emptyCells()
{
    _emptyCells.resize(_tensorSizeCalc._numBoundCells, 0.0);
    _store.addType(&_bufferType);
    _store.initActiveBuffers();
    if (_tensorSizeCalc._numUnboundDims == 0) {
        // In this case each tensor use the same amount of memory and we can re-use previously allocated raw buffers by using free lists.
        _store.enableFreeLists();
    }
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


size_t
DenseTensorStore::getNumCells(const void *buffer) const
{
    const uint32_t *unboundDimSizeEnd = static_cast<const uint32_t *>(buffer);
    const uint32_t *unboundDimSizeStart = unboundDimSizeEnd - _tensorSizeCalc._numUnboundDims;
    size_t numCells = _tensorSizeCalc._numBoundCells;
    for (auto unboundDimSize = unboundDimSizeStart; unboundDimSize != unboundDimSizeEnd; ++unboundDimSize) {
        numCells *= *unboundDimSize;
    }
    return numCells;
}

namespace {

void clearPadAreaAfterBuffer(char *buffer, size_t bufSize, size_t alignedBufSize, uint32_t unboundDimSizesSize) {
    size_t padSize = alignedBufSize - unboundDimSizesSize - bufSize;
    memset(buffer + bufSize, 0, padSize);
}

}

Handle<char>
DenseTensorStore::allocRawBuffer(size_t numCells)
{
    size_t bufSize = numCells * _tensorSizeCalc._cellSize;
    size_t alignedBufSize = alignedSize(numCells);
    auto result = _concreteStore.freeListRawAllocator<char>(_typeId).alloc(alignedBufSize);
    clearPadAreaAfterBuffer(result.data, bufSize, alignedBufSize, unboundDimSizesSize());
    return result;
}

Handle<char>
DenseTensorStore::allocRawBuffer(size_t numCells,
                                 const std::vector<uint32_t> &unboundDimSizes)
{
    assert(unboundDimSizes.size() == _tensorSizeCalc._numUnboundDims);
    auto ret = allocRawBuffer(numCells);
    if (_tensorSizeCalc._numUnboundDims > 0) {
        memcpy(ret.data - unboundDimSizesSize(),
               &unboundDimSizes[0], unboundDimSizesSize());
    }
    assert(numCells == getNumCells(ret.data));
    return ret;
}

void
DenseTensorStore::holdTensor(EntryRef ref)
{
    if (!ref.valid()) {
        return;
    }
    const void *buffer = getRawBuffer(ref);
    size_t numCells = getNumCells(buffer);
    _concreteStore.holdElem(ref, alignedSize(numCells));
}

TensorStore::EntryRef
DenseTensorStore::move(EntryRef ref)
{
    if (!ref.valid()) {
        return RefType();
    }
    auto oldraw = getRawBuffer(ref);
    size_t numCells = getNumCells(oldraw);
    auto newraw = allocRawBuffer(numCells);
    memcpy(newraw.data - unboundDimSizesSize(),
           static_cast<const char *>(oldraw) - unboundDimSizesSize(),
           numCells * _tensorSizeCalc._cellSize + unboundDimSizesSize());
    _concreteStore.holdElem(ref, alignedSize(numCells));
    return newraw.ref;
}

namespace {

void makeConcreteType(MutableDenseTensorView &tensor,
                      const void *buffer,
                      uint32_t numUnboundDims)
{
    const uint32_t *unboundDimSizeEnd = static_cast<const uint32_t *>(buffer);
    const uint32_t *unboundDimSizeBegin = unboundDimSizeEnd - numUnboundDims;
    tensor.setUnboundDimensions(unboundDimSizeBegin, unboundDimSizeEnd);
}

}

std::unique_ptr<Tensor>
DenseTensorStore::getTensor(EntryRef ref) const
{
    using CellsRef = DenseTensorView::CellsRef;
    if (!ref.valid()) {
        return std::unique_ptr<Tensor>();
    }
    auto raw = getRawBuffer(ref);
    size_t numCells = getNumCells(raw);
    if (_tensorSizeCalc._numUnboundDims == 0) {
        return std::make_unique<DenseTensorView>(_type, CellsRef(static_cast<const double *>(raw), numCells));
    } else {
        auto result = std::make_unique<MutableDenseTensorView>(_type, CellsRef(static_cast<const double *>(raw), numCells));
        makeConcreteType(*result, raw, _tensorSizeCalc._numUnboundDims);
        return result;
    }
}

void
DenseTensorStore::getTensor(EntryRef ref, MutableDenseTensorView &tensor) const
{
    if (!ref.valid()) {
        tensor.setCells(DenseTensorView::CellsRef(&_emptyCells[0], _emptyCells.size()));
        if (_tensorSizeCalc._numUnboundDims > 0) {
            tensor.setUnboundDimensionsForEmptyTensor();
        }
    } else {
        auto raw = getRawBuffer(ref);
        size_t numCells = getNumCells(raw);
        tensor.setCells(DenseTensorView::CellsRef(static_cast<const double *>(raw), numCells));
        if (_tensorSizeCalc._numUnboundDims > 0) {
            makeConcreteType(tensor, raw, _tensorSizeCalc._numUnboundDims);
        }
    }
}

namespace {

void
checkMatchingType(const ValueType &lhs, const ValueType &rhs, size_t numCells)
{
    (void) numCells;
    size_t checkNumCells = 1u;
    auto rhsItr = rhs.dimensions().cbegin();
    auto rhsItrEnd = rhs.dimensions().cend();
    (void) rhsItrEnd;
    for (const auto &dim : lhs.dimensions()) {
        (void) dim;
        assert(rhsItr != rhsItrEnd);
        assert(dim.name == rhsItr->name);
        assert(rhsItr->is_bound());
        assert(!dim.is_bound() || dim.size == rhsItr->size);
        checkNumCells *= rhsItr->size;
        ++rhsItr;
    }
    assert(numCells == checkNumCells);
    assert(rhsItr == rhsItrEnd);
}

void
setDenseTensorUnboundDimSizes(void *buffer, const ValueType &lhs, uint32_t numUnboundDims, const ValueType &rhs)
{
    uint32_t *unboundDimSizeEnd = static_cast<uint32_t *>(buffer);
    uint32_t *unboundDimSize = unboundDimSizeEnd - numUnboundDims;
    auto rhsItr = rhs.dimensions().cbegin();
    auto rhsItrEnd = rhs.dimensions().cend();
    (void) rhsItrEnd;
    for (const auto &dim : lhs.dimensions()) {
        assert (rhsItr != rhsItrEnd);
        if (!dim.is_bound()) {
            assert(unboundDimSize != unboundDimSizeEnd);
            *unboundDimSize = rhsItr->size;
            ++unboundDimSize;
        }
        ++rhsItr;
    }
    assert (rhsItr == rhsItrEnd);
    assert(unboundDimSize == unboundDimSizeEnd);
}

}

template <class TensorType>
TensorStore::EntryRef
DenseTensorStore::setDenseTensor(const TensorType &tensor)
{
    size_t numCells = tensor.cellsRef().size();
    checkMatchingType(_type, tensor.type(), numCells);
    auto raw = allocRawBuffer(numCells);
    setDenseTensorUnboundDimSizes(raw.data, _type, _tensorSizeCalc._numUnboundDims, tensor.type());
    memcpy(raw.data, &tensor.cellsRef()[0], numCells * _tensorSizeCalc._cellSize);
    return raw.ref;
}

TensorStore::EntryRef
DenseTensorStore::setTensor(const Tensor &tensor)
{
    const DenseTensorView &view(dynamic_cast<const DenseTensorView &>(tensor));
    return setDenseTensor(view);
}

}
