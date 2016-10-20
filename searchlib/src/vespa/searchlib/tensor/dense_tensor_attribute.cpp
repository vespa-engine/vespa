// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "dense_tensor_attribute.h"
#include <vespa/vespalib/tensor/tensor.h>
#include "dense_tensor_attribute_saver.h"
#include "tensor_attribute.hpp"

using vespalib::eval::ValueType;
using vespalib::tensor::Tensor;
using vespalib::tensor::TensorMapper;

namespace search {

namespace attribute {

namespace {

constexpr uint32_t DENSE_TENSOR_ATTRIBUTE_VERSION = 1;
const vespalib::string tensorTypeTag("tensortype");

class TensorReader : public AttributeVector::ReaderBase
{
private:
    static constexpr uint8_t notPresent = 0;
    static constexpr uint8_t present = 1;
    vespalib::eval::ValueType _tensorType;
    uint32_t _unboundDims;		// number of unbound dimensions
    size_t _numBoundCells;
    std::vector<uint32_t> _dimSizeInfo;  // sizes of unbound dimensions
public:
    TensorReader(AttributeVector &attr)
        : AttributeVector::ReaderBase(attr),
          _tensorType(vespalib::eval::ValueType::from_spec(getDatHeader().getTag(tensorTypeTag).asString())),
          _unboundDims(0),
          _numBoundCells(1),
          _dimSizeInfo()
    {
        for (const auto & dim : _tensorType.dimensions()) {
            if (dim.is_bound()) {
                _numBoundCells *= dim.size;
            } else {
                ++_unboundDims;
            }
        }
        _dimSizeInfo.resize(_unboundDims);
    }
    size_t getNumCells() {
        if (_unboundDims == 0) {
            unsigned char detect;
            _datFile->ReadBuf(&detect, sizeof(detect));
            if (detect == present) {
                return _numBoundCells;
            }
            if (detect == notPresent) {
                return 0u;
            }
            abort(); // bad byte value, should be 0 or 1
        } else {
            _datFile->ReadBuf(&_dimSizeInfo[0], sizeof(uint32_t));
            if (_dimSizeInfo[0] == 0) {
                return 0u;
            }
            size_t numCells = _numBoundCells * _dimSizeInfo[0];
            // TODO: sanity check numCells
            if (_unboundDims > 1) {
                _datFile->ReadBuf(&_dimSizeInfo[1],
                                  (_unboundDims - 1) * sizeof(uint32_t));
                for (auto i = 1u; i < _unboundDims; ++i) {
                    assert(_dimSizeInfo[i] != 0u);
                    numCells *= _dimSizeInfo[i];
                    // TODO: sanity check numCells
                }
            }
            return numCells;
        }
    }
    const vespalib::eval::ValueType &tensorType() const { return _tensorType; }
    const std::vector<uint32_t> &getDimSizeInfo() const { return _dimSizeInfo; }
    void readTensor(void *buf, size_t len) { _datFile->ReadBuf(buf, len); }
};

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
            const auto &dimSizeInfo = tensorReader.getDimSizeInfo();
            auto raw = _denseTensorStore.allocRawBuffer(numCells, dimSizeInfo);
            size_t rawLen = numCells * cellSize;
            tensorReader.readTensor(raw.first, rawLen);
            _refVector.push_back(raw.second);
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
         this->createSaveTargetConfig(),
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

}  // namespace search::attribute

}  // namespace search
