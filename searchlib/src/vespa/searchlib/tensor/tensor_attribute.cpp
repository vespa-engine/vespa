// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_attribute.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/eval/eval/simple_tensor.h>
#include <vespa/eval/tensor/dense/typed_dense_tensor_builder.h>
#include <vespa/eval/tensor/sparse/sparse_tensor.h>
#include <vespa/eval/tensor/wrapped_simple_tensor.h>
#include <vespa/vespalib/util/rcuvector.hpp>

using vespalib::eval::SimpleTensor;
using vespalib::eval::ValueType;
using vespalib::tensor::Tensor;
using vespalib::tensor::TypedDenseTensorBuilder;
using vespalib::tensor::dispatch_0;
using vespalib::tensor::SparseTensor;
using vespalib::tensor::WrappedSimpleTensor;
using document::TensorDataType;
using document::WrongTensorTypeException;

namespace search {

namespace tensor {

namespace {

constexpr uint32_t TENSOR_ATTRIBUTE_VERSION = 0;

// minimum dead bytes in tensor attribute before consider compaction
constexpr size_t DEAD_SLACK = 0x10000u;

struct CallMakeEmptyTensor {
    template <typename CT>
    static Tensor::UP call(const ValueType &type) {
        TypedDenseTensorBuilder<CT> builder(type);
        return builder.build();
    }
};

Tensor::UP
createEmptyTensor(const ValueType &type)
{
    if (type.is_sparse()) {
        return std::make_unique<SparseTensor>(type, SparseTensor::Cells());
    } else if (type.is_dense()) {
        return dispatch_0<CallMakeEmptyTensor>(type.cell_type(), type);
    } else {
        return std::make_unique<WrappedSimpleTensor>(std::make_unique<SimpleTensor>(type, SimpleTensor::Cells()));
    }
}

vespalib::string makeWrongTensorTypeMsg(const ValueType &fieldTensorType, const ValueType &tensorType)
{
    return vespalib::make_string("Field tensor type is '%s' but other tensor type is '%s'",
                                 fieldTensorType.to_spec().c_str(),
                                 tensorType.to_spec().c_str());
}

}

TensorAttribute::TensorAttribute(vespalib::stringref name, const Config &cfg, TensorStore &tensorStore)
    : NotImplementedAttribute(name, cfg),
      _refVector(cfg.getGrowStrategy().getDocsInitialCapacity(),
                 cfg.getGrowStrategy().getDocsGrowPercent(),
                 cfg.getGrowStrategy().getDocsGrowDelta(),
                 getGenerationHolder()),
      _tensorStore(tensorStore),
      _emptyTensor(createEmptyTensor(cfg.tensorType())),
      _compactGeneration(0)
{
}

TensorAttribute::~TensorAttribute() = default;

const ITensorAttribute *
TensorAttribute::asTensorAttribute() const
{
    return this;
}

uint32_t
TensorAttribute::clearDoc(DocId docId)
{
    EntryRef oldRef(_refVector[docId]);
    updateUncommittedDocIdLimit(docId);
    _refVector[docId] = EntryRef();
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
    vespalib::MemoryUsage total = _refVector.getMemoryUsage();
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
    _refVector.push_back(EntryRef());
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
TensorAttribute::checkTensorType(const Tensor &tensor)
{
    const ValueType &fieldTensorType = getConfig().tensorType();
    const ValueType &tensorType = tensor.type();
    if (!TensorDataType::isAssignableType(fieldTensorType, tensorType)) {
        throw WrongTensorTypeException(makeWrongTensorTypeMsg(fieldTensorType, tensorType), VESPA_STRLOC);
    }
}

void
TensorAttribute::setTensorRef(DocId docId, EntryRef ref)
{
    assert(docId < _refVector.size());
    updateUncommittedDocIdLimit(docId);
    // TODO: validate if following fence is sufficient.
    std::atomic_thread_fence(std::memory_order_release);
    // TODO: Check if refVector must consist of std::atomic<EntryRef>
    EntryRef oldRef(_refVector[docId]);
    _refVector[docId] = ref;
    if (oldRef.valid()) {
        _tensorStore.holdTensor(oldRef);
    }
}

Tensor::UP
TensorAttribute::getEmptyTensor() const
{
    return _emptyTensor->clone();
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
        EntryRef &ref = _refVector[lid];
        if (ref.valid()) {
            _tensorStore.holdTensor(ref);
            ref = EntryRef();
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
