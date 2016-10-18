// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "tensor_attribute.h"
#include <vespa/vespalib/tensor/default_tensor.h>
#include <vespa/vespalib/tensor/tensor.h>
#include "tensor_attribute_saver.h"

using vespalib::eval::ValueType;
using vespalib::tensor::Tensor;
using vespalib::tensor::TensorMapper;

namespace search {

namespace attribute {

namespace {

constexpr uint32_t TENSOR_ATTRIBUTE_VERSION = 0;

// minimum dead bytes in tensor attribute before consider compaction
constexpr size_t DEAD_SLACK = 0x10000u;


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

Tensor::UP
createEmptyTensor(const TensorMapper *mapper)
{
    vespalib::tensor::DefaultTensor::builder builder;
    if (mapper != nullptr) {
        return mapper->map(*builder.build());
    }
    return builder.build();
}

bool
shouldCreateMapper(const ValueType &tensorType)
{
    return tensorType.is_tensor() && !tensorType.dimensions().empty();
}

}

TensorAttribute::TensorAttribute(const vespalib::stringref &baseFileName,
                                 const Config &cfg)
    : NotImplementedAttribute(baseFileName, cfg),
      _refVector(cfg.getGrowStrategy().getDocsInitialCapacity(),
                 cfg.getGrowStrategy().getDocsGrowPercent(),
                 cfg.getGrowStrategy().getDocsGrowDelta(),
                 getGenerationHolder()),
      _tensorStore(),
      _tensorMapper(),
      _compactGeneration(0)
{
    if (shouldCreateMapper(cfg.tensorType())) {
        _tensorMapper = std::make_unique<TensorMapper>(cfg.tensorType());
    }
}


TensorAttribute::~TensorAttribute()
{
    getGenerationHolder().clearHoldLists();
    _tensorStore.clearHoldLists();
}



uint32_t
TensorAttribute::clearDoc(DocId docId)
{
    RefType oldRef(_refVector[docId]);
    updateUncommittedDocIdLimit(docId);
    _refVector[docId] = RefType();
    if (oldRef.valid()) {
        _tensorStore.holdTensor(oldRef);
        return 1u;
    }
    return 0u;
}


void
TensorAttribute::compactWorst()
{
    uint32_t bufferId = _tensorStore.startCompactWorstBuffer();
    size_t lidLimit = _refVector.size();
    for (uint32_t lid = 0; lid < lidLimit; ++lid) {
        RefType ref = _refVector[lid];
        if (ref.valid() && ref.bufferId() == bufferId) {
            RefType newRef = _tensorStore.move(ref);
            // TODO: validate if following fence is sufficient.
            std::atomic_thread_fence(std::memory_order_release);
            _refVector[lid] = newRef;
        }
    }
    _tensorStore.finishCompactWorstBuffer(bufferId);
    _compactGeneration = getCurrentGeneration();
    incGeneration();
    updateStat(true);
}

void
TensorAttribute::onCommit()
{
    // Note: Cost can be reduced if unneeded generation increments are dropped
    incGeneration();
    if (getFirstUsedGeneration() > _compactGeneration) {
        // No data held from previous compact operation
        Status &status = getStatus();
        size_t used = status.getUsed();
        size_t dead = status.getDead();
        if ((dead >= DEAD_SLACK) && (dead * 5 > used)) {
            compactWorst();
        }
    }
}


void
TensorAttribute::onUpdateStat()
{
    // update statistics
    MemoryUsage total = _refVector.getMemoryUsage();
    total.merge(_tensorStore.getMemoryUsage());
    total.incAllocatedBytesOnHold(getGenerationHolder().getHeldBytes());
    this->updateStatistics(_refVector.size(),
                           _refVector.size(),
                           total.allocatedBytes(),
                           total.usedBytes(),
                           total.deadBytes(),
                           total.allocatedBytesOnHold());
}


void
TensorAttribute::removeOldGenerations(generation_t firstUsed)
{
    _tensorStore.trimHoldLists(firstUsed);
    getGenerationHolder().trimHoldLists(firstUsed);
}

void
TensorAttribute::onGenerationChange(generation_t generation)
{
    getGenerationHolder().transferHoldLists(generation - 1);
    _tensorStore.transferHoldLists(generation - 1);
}


bool
TensorAttribute::addDoc(DocId &docId)
{
    bool incGen = _refVector.isFull();
    _refVector.push_back(RefType());
    AttributeVector::incNumDocs();
    docId = AttributeVector::getNumDocs() - 1;
    updateUncommittedDocIdLimit(docId);
    if (incGen) {
        incGeneration();
    } else {
        removeAllOldGenerations();
    }
    return true;
}


void
TensorAttribute::setTensor(DocId docId, const Tensor &tensor)
{
    assert(docId < _refVector.size());
    updateUncommittedDocIdLimit(docId);
    // TODO: Handle generic tensor attribute in a better way ?
    RefType ref = _tensorStore.setTensor(
            (_tensorMapper ? *_tensorMapper->map(tensor) : tensor));
    // TODO: validate if following fence is sufficient.
    std::atomic_thread_fence(std::memory_order_release);
    // TODO: Check if refVector must consist of std::atomic<RefType>
    _refVector[docId] = ref;
}


std::unique_ptr<Tensor>
TensorAttribute::getTensor(DocId docId) const
{
    RefType ref;
    if (docId < getCommittedDocIdLimit()) {
        ref = _refVector[docId];
    }
    if (!ref.valid()) {
        return std::unique_ptr<Tensor>();
    }
    return _tensorStore.getTensor(ref);
}

Tensor::UP
TensorAttribute::getEmptyTensor() const
{
    return createEmptyTensor(_tensorMapper.get());
}

void
TensorAttribute::clearDocs(DocId lidLow, DocId lidLimit)
{
    assert(lidLow <= lidLimit);
    assert(lidLimit <= this->getNumDocs());
    for (DocId lid = lidLow; lid < lidLimit; ++lid) {
        RefType &ref = _refVector[lid];
        if (ref.valid()) {
            _tensorStore.holdTensor(ref);
            ref = RefType();
        }
    }
}


void
TensorAttribute::onShrinkLidSpace()
{
    // Tensors for lids > committedDocIdLimit have been cleared.
    uint32_t committedDocIdLimit = getCommittedDocIdLimit();
    assert(_refVector.size() >= committedDocIdLimit);
    _refVector.shrink(committedDocIdLimit);
    setNumDocs(committedDocIdLimit);
}


bool
TensorAttribute::onLoad()
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
        auto raw = _tensorStore.allocRawBuffer(tensorSize);
        if (tensorSize != 0) {
            tensorReader.readTensor(raw.first, tensorSize);
        }
        _refVector.push_back(raw.second);
    }
    setNumDocs(numDocs);
    setCommittedDocIdLimit(numDocs);
    return true;
}


uint32_t
TensorAttribute::getVersion() const
{
    return TENSOR_ATTRIBUTE_VERSION;
}


TensorAttribute::RefCopyVector
TensorAttribute::getRefCopy() const
{
    uint32_t size = getCommittedDocIdLimit();
    assert(size <= _refVector.size());
    return RefCopyVector(&_refVector[0], &_refVector[0] + size);
}

std::unique_ptr<AttributeSaver>
TensorAttribute::onInitSave()
{
    vespalib::GenerationHandler::Guard guard(getGenerationHandler().
                                             takeGuard());
    return std::make_unique<TensorAttributeSaver>
        (std::move(guard),
         this->createSaveTargetConfig(),
         getRefCopy(),
         _tensorStore);
}

}  // namespace search::attribute

}  // namespace search
