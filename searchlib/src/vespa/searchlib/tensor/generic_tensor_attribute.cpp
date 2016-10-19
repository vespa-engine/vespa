// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "generic_tensor_attribute.h"
#include <vespa/vespalib/tensor/tensor.h>
#include "tensor_attribute_saver.h"

using vespalib::eval::ValueType;
using vespalib::tensor::Tensor;
using vespalib::tensor::TensorMapper;

namespace search {

namespace attribute {

namespace {

constexpr uint32_t TENSOR_ATTRIBUTE_VERSION = 0;

class TensorReader : public AttributeVector::ReaderBase
{
private:
    FileReader<uint32_t> _tensorSizeReader;
public:
    TensorReader(AttributeVector &attr)
        : AttributeVector::ReaderBase(attr),
          _tensorSizeReader(*_datFile)
    {
    }
    uint32_t getNextTensorSize() { return _tensorSizeReader.readHostOrder(); }
    void readTensor(void *buf, size_t len) { _datFile->ReadBuf(buf, len); }
};

}

GenericTensorAttribute::GenericTensorAttribute(const vespalib::stringref &baseFileName,
                                 const Config &cfg)
    : TensorAttribute(baseFileName, cfg, _genericTensorStore)
{
}


GenericTensorAttribute::~GenericTensorAttribute()
{
    getGenerationHolder().clearHoldLists();
    _tensorStore.clearHoldLists();
}

void
GenericTensorAttribute::setTensor(DocId docId, const Tensor &tensor)
{
    RefType ref = _genericTensorStore.setTensor(
            (_tensorMapper ? *_tensorMapper->map(tensor) : tensor));
    setTensorRef(docId, ref);
}


std::unique_ptr<Tensor>
GenericTensorAttribute::getTensor(DocId docId) const
{
    RefType ref;
    if (docId < getCommittedDocIdLimit()) {
        ref = _refVector[docId];
    }
    if (!ref.valid()) {
        return std::unique_ptr<Tensor>();
    }
    return _genericTensorStore.getTensor(ref);
}

bool
GenericTensorAttribute::onLoad()
{
    TensorReader tensorReader(*this);
    if (!tensorReader.hasData()) {
        return false;
    }
    setCreateSerialNum(tensorReader.getCreateSerialNum());
    assert(tensorReader.getVersion() == TENSOR_ATTRIBUTE_VERSION);
    uint32_t numDocs(tensorReader.getDocIdLimit());
    _refVector.reset();
    _refVector.unsafe_reserve(numDocs);
    for (uint32_t lid = 0; lid < numDocs; ++lid) {
        uint32_t tensorSize = tensorReader.getNextTensorSize();
        auto raw = _genericTensorStore.allocRawBuffer(tensorSize);
        if (tensorSize != 0) {
            tensorReader.readTensor(raw.first, tensorSize);
        }
        _refVector.push_back(raw.second);
    }
    setNumDocs(numDocs);
    setCommittedDocIdLimit(numDocs);
    return true;
}


std::unique_ptr<AttributeSaver>
GenericTensorAttribute::onInitSave()
{
    vespalib::GenerationHandler::Guard guard(getGenerationHandler().
                                             takeGuard());
    return std::make_unique<TensorAttributeSaver>
        (std::move(guard),
         this->createSaveTargetConfig(),
         getRefCopy(),
         _genericTensorStore);
}

}  // namespace search::attribute

}  // namespace search
