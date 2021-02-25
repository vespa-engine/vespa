// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_attribute.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/searchlib/util/state_explorer_utils.h>
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/data/slime/inserter.h>
#include <vespa/vespalib/util/rcuvector.hpp>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>

using document::TensorDataType;
using document::TensorUpdate;
using document::WrongTensorTypeException;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;
using vespalib::eval::ValueType;
using search::StateExplorerUtils;

namespace search::tensor {

namespace {

constexpr uint32_t TENSOR_ATTRIBUTE_VERSION = 0;

// minimum dead bytes in tensor attribute before consider compaction
constexpr size_t DEAD_SLACK = 0x10000u;

Value::UP
createEmptyTensor(const ValueType &type)
{
    const auto &factory = FastValueBuilderFactory::get();
    TensorSpec empty_spec(type.to_spec());
    return vespalib::eval::value_from_spec(empty_spec, factory);
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
    vespalib::MemoryUsage total = memory_usage();
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
TensorAttribute::checkTensorType(const vespalib::eval::Value &tensor)
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

vespalib::MemoryUsage
TensorAttribute::memory_usage() const
{
    vespalib::MemoryUsage result = _refVector.getMemoryUsage();
    result.merge(_tensorStore.getMemoryUsage());
    result.mergeGenerationHeldBytes(getGenerationHolder().getHeldBytes());
    return result;
}

void
TensorAttribute::populate_state(vespalib::slime::Cursor& object) const
{
    object.setLong("compact_generation", _compactGeneration);
    StateExplorerUtils::memory_usage_to_slime(_refVector.getMemoryUsage(),
                                              object.setObject("ref_vector").setObject("memory_usage"));
    StateExplorerUtils::memory_usage_to_slime(_tensorStore.getMemoryUsage(),
                                              object.setObject("tensor_store").setObject("memory_usage"));
}

vespalib::eval::Value::UP
TensorAttribute::getEmptyTensor() const
{
    return FastValueBuilderFactory::get().copy(*_emptyTensor);
}

vespalib::eval::TypedCells
TensorAttribute::extract_cells_ref(uint32_t /*docid*/) const
{
    notImplemented();
}

const vespalib::eval::Value&
TensorAttribute::get_tensor_ref(uint32_t /*docid*/) const
{
    notImplemented();
}

const vespalib::eval::ValueType &
TensorAttribute::getTensorType() const
{
    return getConfig().tensorType();
}

void
TensorAttribute::get_state(const vespalib::slime::Inserter& inserter) const
{
    auto& object = inserter.insertObject();
    populate_state(object);
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

void
TensorAttribute::update_tensor(DocId docId,
                               const document::TensorUpdate &update,
                               bool create_empty_if_non_existing)
{
    const vespalib::eval::Value * old_v = nullptr;
    auto old_tensor = getTensor(docId);
    if (old_tensor) {
        old_v = old_tensor.get();
    } else if (create_empty_if_non_existing) {
        old_v = _emptyTensor.get();
    } else {
        return;
    }
    auto new_value = update.apply_to(*old_v, FastValueBuilderFactory::get());
    if (new_value) {
        setTensor(docId, *new_value);
    }
}

std::unique_ptr<PrepareResult>
TensorAttribute::prepare_set_tensor(DocId docid, const vespalib::eval::Value& tensor) const
{
    (void) docid;
    (void) tensor;
    return std::unique_ptr<PrepareResult>();
}

void
TensorAttribute::complete_set_tensor(DocId docid, const vespalib::eval::Value& tensor,
                                     std::unique_ptr<PrepareResult> prepare_result)
{
    (void) docid;
    (void) tensor;
    (void) prepare_result;
}

IMPLEMENT_IDENTIFIABLE_ABSTRACT(TensorAttribute, AttributeVector);

}
