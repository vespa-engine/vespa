// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_attribute.h"
#include <vespa/eval/tensor/default_tensor.h>
#include <vespa/searchlib/common/rcuvector.hpp>

using vespalib::eval::ValueType;
using vespalib::tensor::Tensor;
using vespalib::tensor::TensorMapper;

namespace search {

namespace tensor {

namespace {

constexpr uint32_t TENSOR_ATTRIBUTE_VERSION = 0;

// minimum dead bytes in tensor attribute before consider compaction
constexpr size_t DEAD_SLACK = 0x10000u;


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
                                 const Config &cfg,
                                 TensorStore &tensorStore)
    : NotImplementedAttribute(baseFileName, cfg),
      _refVector(cfg.getGrowStrategy().getDocsInitialCapacity(),
                 cfg.getGrowStrategy().getDocsGrowPercent(),
                 cfg.getGrowStrategy().getDocsGrowDelta(),
                 getGenerationHolder()),
      _tensorStore(tensorStore),
      _tensorMapper(),
      _compactGeneration(0)
{
    if (shouldCreateMapper(cfg.tensorType())) {
        _tensorMapper = std::make_unique<TensorMapper>(cfg.tensorType());
    }
}


TensorAttribute::~TensorAttribute()
{
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
    total.mergeGenerationHeldBytes(getGenerationHolder().getHeldBytes());
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
TensorAttribute::setTensorRef(DocId docId, RefType ref)
{
    assert(docId < _refVector.size());
    updateUncommittedDocIdLimit(docId);
    // TODO: validate if following fence is sufficient.
    std::atomic_thread_fence(std::memory_order_release);
    // TODO: Check if refVector must consist of std::atomic<RefType>
    RefType oldRef(_refVector[docId]);
    _refVector[docId] = ref;
    if (oldRef.valid()) {
        _tensorStore.holdTensor(oldRef);
    }
}

Tensor::UP
TensorAttribute::getEmptyTensor() const
{
    return createEmptyTensor(_tensorMapper.get());
}

vespalib::eval::ValueType
TensorAttribute::getTensorType() const
{
    return getConfig().tensorType();
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

IMPLEMENT_IDENTIFIABLE_ABSTRACT(TensorAttribute, AttributeVector);

}  // namespace search::tensor

}  // namespace search
