// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_attribute.h"
#include <vespa/log/log.h>
LOG_SETUP(".searchlib.tensor.dense_tensor_attribute");
#include "dense_tensor_attribute_saver.h"
#include "tensor_attribute.hpp"
#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/dense/mutable_dense_tensor_view.h>
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/searchlib/attribute/readerbase.h>

using vespalib::eval::ValueType;
using vespalib::tensor::MutableDenseTensorView;
using vespalib::tensor::Tensor;
using vespalib::tensor::TensorMapper;

namespace search {

namespace tensor {

namespace {

constexpr uint32_t DENSE_TENSOR_ATTRIBUTE_VERSION = 1;
const vespalib::string tensorTypeTag("tensortype");

class TensorReader : public ReaderBase
{
private:
    static constexpr uint8_t tensorIsNotPresent = 0;
    static constexpr uint8_t tensorIsPresent = 1;
    vespalib::eval::ValueType _tensorType;
    uint32_t _numUnboundDims;
    size_t _numBoundCells;
    std::vector<uint32_t> _unboundDimSizes;
public:
    TensorReader(AttributeVector &attr);
    ~TensorReader();
    size_t getNumCells();
    const vespalib::eval::ValueType &tensorType() const { return _tensorType; }
    const std::vector<uint32_t> &getUnboundDimSizes() const { return _unboundDimSizes; }
    void readTensor(void *buf, size_t len) { _datFile->ReadBuf(buf, len); }
};

TensorReader::TensorReader(AttributeVector &attr)
    : ReaderBase(attr),
    _tensorType(vespalib::eval::ValueType::from_spec(getDatHeader().getTag(tensorTypeTag).asString())),
    _numUnboundDims(0),
    _numBoundCells(1),
    _unboundDimSizes()
{
    for (const auto & dim : _tensorType.dimensions()) {
        if (dim.is_bound()) {
            _numBoundCells *= dim.size;
        } else {
            ++_numUnboundDims;
        }
    }
    _unboundDimSizes.resize(_numUnboundDims);
}
TensorReader::~TensorReader() { }

size_t
TensorReader::getNumCells() {
    unsigned char detect;
    _datFile->ReadBuf(&detect, sizeof(detect));
    if (detect == tensorIsNotPresent) {
        return 0u;
    }
    if (detect != tensorIsPresent) {
        LOG_ABORT("should not be reached");
    }
    size_t numCells = _numBoundCells;
    if (_numUnboundDims != 0) {
        _datFile->ReadBuf(&_unboundDimSizes[0],
                          _numUnboundDims * sizeof(uint32_t));
        for (auto i = 0u; i < _numUnboundDims; ++i) {
            assert(_unboundDimSizes[i] != 0u);
            numCells *= _unboundDimSizes[i];
            // TODO: sanity check numCells
        }
    }
    return numCells;
}

}

DenseTensorAttribute::DenseTensorAttribute(const vespalib::stringref &baseFileName,
                                 const Config &cfg)
    : TensorAttribute(baseFileName, cfg, _denseTensorStore),
      _denseTensorStore(cfg.tensorType())
{
}


DenseTensorAttribute::~DenseTensorAttribute()
{
    getGenerationHolder().clearHoldLists();
    _tensorStore.clearHoldLists();
}

void
DenseTensorAttribute::setTensor(DocId docId, const Tensor &tensor)
{
    RefType ref = _denseTensorStore.setTensor(
            (_tensorMapper ? *_tensorMapper->map(tensor) : tensor));
    setTensorRef(docId, ref);
}


std::unique_ptr<Tensor>
DenseTensorAttribute::getTensor(DocId docId) const
{
    RefType ref;
    if (docId < getCommittedDocIdLimit()) {
        ref = _refVector[docId];
    }
    if (!ref.valid()) {
        return std::unique_ptr<Tensor>();
    }
    return _denseTensorStore.getTensor(ref);
}

void
DenseTensorAttribute::getTensor(DocId docId, MutableDenseTensorView &tensor) const
{
    RefType ref;
    if (docId < getCommittedDocIdLimit()) {
        ref = _refVector[docId];
    }
    _denseTensorStore.getTensor(ref, tensor);
}

bool
DenseTensorAttribute::onLoad()
{
    TensorReader tensorReader(*this);
    if (!tensorReader.hasData()) {
        return false;
    }
    setCreateSerialNum(tensorReader.getCreateSerialNum());
    assert(tensorReader.getVersion() == DENSE_TENSOR_ATTRIBUTE_VERSION);
    assert(getConfig().tensorType().to_spec() ==
           tensorReader.getDatHeader().getTag(tensorTypeTag).asString());
    uint32_t numDocs(tensorReader.getDocIdLimit());
    uint32_t cellSize(_denseTensorStore.getCellSize());
    _refVector.reset();
    _refVector.unsafe_reserve(numDocs);
    for (uint32_t lid = 0; lid < numDocs; ++lid) {
        size_t numCells = tensorReader.getNumCells();
        if (numCells != 0u) {
            const auto &unboundDimSizes = tensorReader.getUnboundDimSizes();
            auto raw = _denseTensorStore.allocRawBuffer(numCells, unboundDimSizes);
            size_t rawLen = numCells * cellSize;
            tensorReader.readTensor(raw.data, rawLen);
            _refVector.push_back(raw.ref);
        } else {
            _refVector.push_back(RefType());
        }
    }
    setNumDocs(numDocs);
    setCommittedDocIdLimit(numDocs);
    return true;
}


std::unique_ptr<AttributeSaver>
DenseTensorAttribute::onInitSave()
{
    vespalib::GenerationHandler::Guard guard(getGenerationHandler().
                                             takeGuard());
    return std::make_unique<DenseTensorAttributeSaver>
        (std::move(guard),
         this->createAttributeHeader(),
         getRefCopy(),
         _denseTensorStore);
}

void
DenseTensorAttribute::compactWorst()
{
    doCompactWorst<DenseTensorStore::RefType>();
}

uint32_t
DenseTensorAttribute::getVersion() const
{
    return DENSE_TENSOR_ATTRIBUTE_VERSION;
}

}  // namespace search::tensor

}  // namespace search
