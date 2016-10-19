// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "dense_tensor_store.h"
#include <vespa/vespalib/tensor/tensor.h>
#include <vespa/vespalib/tensor/dense/dense_tensor_view.h>
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
using vespalib::ConstArrayRef;

namespace search {
namespace attribute {

constexpr size_t MIN_BUFFER_CLUSTERS = 1024;

namespace {

size_t
calcCellsSize(const vespalib::eval::ValueType &type)
{
    size_t cellsSize = 1;
    for (const auto &dim : type.dimensions()) {
        cellsSize *= dim.size;
    }
    return cellsSize;
}

}

DenseTensorStore::DenseTensorStore(const ValueType &type)
    : TensorStore(_concreteStore),
      _concreteStore(),
      _bufferType(RefType::align(1),
                  MIN_BUFFER_CLUSTERS,
                  RefType::offsetSize() / RefType::align(1)),
      _type(type),
      _numCells(calcCellsSize(_type))
{
    _store.addType(&_bufferType);
    _store.initActiveBuffers();
}

DenseTensorStore::~DenseTensorStore()
{
    _store.dropBuffers();
}

const double *
DenseTensorStore::getRawBuffer(RefType ref) const
{
    if (!ref.valid()) {
        return nullptr;
    }
    return _store.getBufferEntry<double>(ref.bufferId(),
                                         ref.offset());
}

std::pair<double *, DenseTensorStore::RefType>
DenseTensorStore::allocRawBuffer()
{
    size_t bufSize = RefType::align(_numCells);
    _store.ensureBufferCapacity(_typeId, bufSize);
    uint32_t activeBufferId = _store.getActiveBufferId(_typeId);
    btree::BufferState &state = _store.getBufferState(activeBufferId);
    size_t oldSize = state.size();
    double *bufferEntryWritePtr =
        _store.getBufferEntry<double>(activeBufferId, oldSize);
    double *padWritePtr = bufferEntryWritePtr + _numCells;
    for (size_t i = _numCells; i < bufSize; ++i) {
        *padWritePtr++ = 0.0;
    }
    state.pushed_back(bufSize);
    return std::make_pair(bufferEntryWritePtr,
                          RefType(oldSize, activeBufferId));
}

void
DenseTensorStore::holdTensor(EntryRef ref)
{
    if (!ref.valid()) {
        return;
    }
    _concreteStore.holdElem(ref, _numCells);
}

TensorStore::EntryRef
DenseTensorStore::move(EntryRef ref) {
    if (!ref.valid()) {
        return RefType();
    }
    auto oldraw = getRawBuffer(ref);
    auto newraw = allocRawBuffer();
    memcpy(newraw.first, oldraw, _numCells * sizeof(double));
    _concreteStore.holdElem(ref, _numCells);
    return newraw.second;
}

std::unique_ptr<Tensor>
DenseTensorStore::getTensor(EntryRef ref) const
{
    auto raw = getRawBuffer(ref);
    if (raw == nullptr) {
        return std::unique_ptr<Tensor>();
    }
    return std::make_unique<DenseTensorView>(_type, ConstArrayRef<double>(raw, _numCells));
}

template <class TensorType>
TensorStore::EntryRef
DenseTensorStore::setDenseTensor(const TensorType &tensor)
{
    assert(tensor.type() == _type);
    assert(tensor.cells().size() == _numCells);
    auto raw = allocRawBuffer();
    memcpy(raw.first, &tensor.cells()[0], _numCells * sizeof(double));
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
