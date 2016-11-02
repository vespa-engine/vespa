// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "dense_tensor_store.h"
#include <vespa/vespalib/tensor/tensor.h>
#include <vespa/vespalib/tensor/dense/dense_tensor_view.h>
#include <vespa/vespalib/tensor/dense/mutable_dense_tensor_view.h>
#include <vespa/vespalib/tensor/dense/dense_tensor.h>
#include <vespa/vespalib/tensor/serialization/typed_binary_format.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/macro.h>
#include <vespa/document/util/serializable.h>
#include <vespa/searchlib/btree/datastore.hpp>

using vespalib::tensor::Tensor;
using vespalib::tensor::DenseTensor;
using vespalib::tensor::DenseTensorView;
using vespalib::tensor::MutableDenseTensorView;
using vespalib::eval::ValueType;

namespace search {
namespace attribute {

constexpr size_t MIN_BUFFER_CLUSTERS = 1024;

DenseTensorStore::BufferType::BufferType()
    : btree::BufferType<char>(RefType::align(1),
                              MIN_BUFFER_CLUSTERS,
                              RefType::offsetSize() / RefType::align(1)),
      _unboundDimSizesSize(0u)
{
}

DenseTensorStore::BufferType::~BufferType()
{
}

void
DenseTensorStore::BufferType::cleanHold(void *buffer, uint64_t offset,
                                        uint64_t len)
{
    // Clear both tensor dimension size information and cells.
    memset(static_cast<char *>(buffer) + offset - _unboundDimSizesSize, 0, len);
}


DenseTensorStore::DenseTensorStore(const ValueType &type)
    : TensorStore(_concreteStore),
      _concreteStore(),
      _bufferType(),
      _type(type),
      _numBoundCells(1u),
      _numUnboundDims(0u),
      _cellSize(sizeof(double)),
      _emptyCells()
{
    for (const auto & dim : _type.dimensions()) {
        if (dim.is_bound()) {
            _numBoundCells *= dim.size;
        } else {
            ++_numUnboundDims;
        }
    }
    _emptyCells.resize(_numBoundCells, 0.0);
    _bufferType.setUnboundDimSizesSize(_numUnboundDims * sizeof(uint32_t));
    _store.addType(&_bufferType);
    _store.initActiveBuffers();
}

DenseTensorStore::~DenseTensorStore()
{
    _store.dropBuffers();
}

const void *
DenseTensorStore::getRawBuffer(RefType ref) const
{
    return _store.getBufferEntry<char>(ref.bufferId(),
                                         ref.offset());
}


size_t
DenseTensorStore::getNumCells(const void *buffer) const
{
    const uint32_t *unboundDimSizeEnd = static_cast<const uint32_t *>(buffer);
    const uint32_t *unboundDimSizeStart = unboundDimSizeEnd - _numUnboundDims;
    size_t numCells = _numBoundCells;
    for (auto unboundDimSize = unboundDimSizeStart; unboundDimSize != unboundDimSizeEnd; ++unboundDimSize) {
        numCells *= *unboundDimSize;
    }
    return numCells;
}

namespace {

void allocateSpaceForFirstUnboundDimSizesInBuffer(char *&buffer, size_t &oldSize, btree::BufferState &state, size_t alignedUnboundDimSizesSize) {
    memset(buffer, 0, alignedUnboundDimSizesSize);
    state.pushed_back(alignedUnboundDimSizesSize);
    state._deadElems += alignedUnboundDimSizesSize;
    buffer += alignedUnboundDimSizesSize;
    oldSize += alignedUnboundDimSizesSize;
}

void clearPadAreaAfterBuffer(char *buffer, size_t bufSize, size_t alignedBufSize, uint32_t unboundDimSizesSize) {
    size_t padSize = alignedBufSize - unboundDimSizesSize - bufSize;
    memset(buffer + bufSize, 0, padSize);
}

}

std::pair<void *, DenseTensorStore::RefType>
DenseTensorStore::allocRawBuffer(size_t numCells)
{
    size_t alignedUnboundDimSizesSize = RefType::align(unboundDimSizesSize());
    size_t bufSize = numCells * _cellSize;
    size_t alignedBufSize = alignedSize(numCells);
    size_t ensureSize = alignedBufSize + alignedUnboundDimSizesSize;
    _store.ensureBufferCapacity(_typeId, ensureSize);
    uint32_t activeBufferId = _store.getActiveBufferId(_typeId);
    btree::BufferState &state = _store.getBufferState(activeBufferId);
    size_t oldSize = state.size();
    char *buffer = _store.getBufferEntry<char>(activeBufferId, oldSize);
    if (oldSize <= alignedUnboundDimSizesSize) {
        allocateSpaceForFirstUnboundDimSizesInBuffer(buffer, oldSize, state, alignedUnboundDimSizesSize);
    }
    clearPadAreaAfterBuffer(buffer, bufSize, alignedBufSize, unboundDimSizesSize());
    state.pushed_back(alignedBufSize);
    return std::make_pair(buffer, RefType(oldSize, activeBufferId));
}

std::pair<void *, DenseTensorStore::RefType>
DenseTensorStore::allocRawBuffer(size_t numCells,
                                 const std::vector<uint32_t> &unboundDimSizes)
{
    assert(unboundDimSizes.size() == _numUnboundDims);
    auto ret = allocRawBuffer(numCells);
    if (_numUnboundDims > 0) {
        memcpy(static_cast<char *>(ret.first) - unboundDimSizesSize(),
               &unboundDimSizes[0], unboundDimSizesSize());
    }
    assert(numCells == getNumCells(ret.first));
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
    memcpy(static_cast<char *>(newraw.first) - unboundDimSizesSize(),
           static_cast<const char *>(oldraw) - unboundDimSizesSize(),
           numCells * _cellSize + unboundDimSizesSize());
    _concreteStore.holdElem(ref, alignedSize(numCells));
    return newraw.second;
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
    if (!ref.valid()) {
        return std::unique_ptr<Tensor>();
    }
    auto raw = getRawBuffer(ref);
    size_t numCells = getNumCells(raw);
    if (_numUnboundDims == 0) {
        return std::make_unique<DenseTensorView>
                (_type,
                 DenseTensorView::CellsRef(static_cast<const double *>(raw), numCells));
    } else {
        std::unique_ptr <MutableDenseTensorView> result =
                std::make_unique<MutableDenseTensorView>(_type,
                                                         DenseTensorView::CellsRef(static_cast<const double *>(raw),
                                                                               numCells));
        makeConcreteType(*result, raw, _numUnboundDims);
        return result;
    }
}

void
DenseTensorStore::getTensor(EntryRef ref, MutableDenseTensorView &tensor) const
{
    if (!ref.valid()) {
        tensor.setCells(DenseTensorView::CellsRef(&_emptyCells[0], _emptyCells.size()));
        if (_numUnboundDims > 0) {
            tensor.setUnboundDimensionsForEmptyTensor();
        }
    } else {
        auto raw = getRawBuffer(ref);
        size_t numCells = getNumCells(raw);
        tensor.setCells(DenseTensorView::CellsRef(static_cast<const double *>(raw), numCells));
        if (_numUnboundDims > 0) {
            makeConcreteType(tensor, raw, _numUnboundDims);
        }
    }
}

namespace
{

void
checkMatchingType(const ValueType &lhs, const ValueType &rhs, size_t numCells)
{
    size_t checkNumCells = 1u;
    auto rhsItr = rhs.dimensions().cbegin();
    auto rhsItrEnd = rhs.dimensions().cend();
    for (const auto &dim : lhs.dimensions()) {
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
    size_t numCells = tensor.cells().size();
    checkMatchingType(_type, tensor.type(), numCells);
    auto raw = allocRawBuffer(numCells);
    setDenseTensorUnboundDimSizes(raw.first, _type, _numUnboundDims, tensor.type());
    memcpy(raw.first, &tensor.cells()[0], numCells * _cellSize);
    return raw.second;
}

TensorStore::EntryRef
DenseTensorStore::setTensor(const Tensor &tensor)
{
    const DenseTensorView *view(dynamic_cast<const DenseTensorView *>(&tensor));
    if (view) {
        return setDenseTensor(*view);
    }
    const DenseTensor *dense(dynamic_cast<const DenseTensor *>(&tensor));
    if (dense) {
        return setDenseTensor(*dense);
    }
    abort();
}

}  // namespace search::attribute
}  // namespace search
