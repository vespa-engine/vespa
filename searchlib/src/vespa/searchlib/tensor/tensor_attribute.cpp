// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_attribute.h"
#include "nearest_neighbor_index.h"
#include "nearest_neighbor_index_factory.h"
#include "nearest_neighbor_index_saver.h"
#include "serialized_tensor_ref.h"
#include "tensor_attribute_constants.h"
#include "tensor_attribute_loader.h"
#include "tensor_attribute_saver.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/searchlib/attribute/address_space_components.h>
#include <vespa/searchlib/util/state_explorer_utils.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/data/slime/inserter.h>
#include <vespa/vespalib/datastore/i_compaction_context.h>
#include <vespa/vespalib/util/shared_string_repo.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>

using document::TensorDataType;
using document::TensorUpdate;
using document::WrongTensorTypeException;
using search::AddressSpaceComponents;
using search::StateExplorerUtils;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;
using vespalib::eval::ValueType;
using vespalib::slime::ObjectInserter;

namespace search::tensor {

namespace {

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

TensorAttribute::TensorAttribute(vespalib::stringref name, const Config &cfg, TensorStore &tensorStore, const NearestNeighborIndexFactory& index_factory)
    : NotImplementedAttribute(name, cfg),
      _refVector(cfg.getGrowStrategy(), getGenerationHolder()),
      _tensorStore(tensorStore),
      _distance_function_factory(make_distance_function_factory(cfg.distance_metric(), cfg.tensorType().cell_type())),
      _index(),
      _is_dense(cfg.tensorType().is_dense()),
      _emptyTensor(createEmptyTensor(cfg.tensorType())),
      _compactGeneration(0),
      _subspace_type(cfg.tensorType()),
      _comp(cfg.tensorType())
{
    if (cfg.hnsw_index_params().has_value()) {
        auto tensor_type = cfg.tensorType();
        size_t vector_size = tensor_type.dense_subspace_size();
        _index = index_factory.make(*this, vector_size, !_is_dense, tensor_type.cell_type(), cfg.hnsw_index_params().value());
    }
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
    consider_remove_from_index(docId);
    updateUncommittedDocIdLimit(docId);
    auto& elem_ref = _refVector[docId];
    EntryRef oldRef(elem_ref.load_relaxed());
    elem_ref.store_relaxed(EntryRef());
    if (oldRef.valid()) {
        _tensorStore.holdTensor(oldRef);
        return 1u;
    }
    return 0u;
}

void
TensorAttribute::onCommit()
{
    incGeneration();
    if (_tensorStore.consider_compact()) {
        auto context = _tensorStore.start_compact(getConfig().getCompactionStrategy());
        if (context) {
            context->compact(vespalib::ArrayRef<AtomicEntryRef>(&_refVector[0], _refVector.size()));
        }
        _compactGeneration = getCurrentGeneration();
        incGeneration();
        updateStat(true);
    }
    if (_index) {
        if (_index->consider_compact(getConfig().getCompactionStrategy())) {
            incGeneration();
            updateStat(true);
        }
    }
}

void
TensorAttribute::onUpdateStat()
{
    vespalib::MemoryUsage total = update_stat();
    this->updateStatistics(_refVector.size(),
                           _refVector.size(),
                           total.allocatedBytes(),
                           total.usedBytes(),
                           total.deadBytes(),
                           total.allocatedBytesOnHold());
}

void
TensorAttribute::reclaim_memory(generation_t oldest_used_gen)
{
    _tensorStore.reclaim_memory(oldest_used_gen);
    getGenerationHolder().reclaim(oldest_used_gen);
    if (_index) {
        _index->reclaim_memory(oldest_used_gen);
    }
}

void
TensorAttribute::before_inc_generation(generation_t current_gen)
{
    getGenerationHolder().assign_generation(current_gen);
    _tensorStore.assign_generation(current_gen);
    if (_index) {
        _index->assign_generation(current_gen);
    }
}

bool
TensorAttribute::addDoc(DocId &docId)
{
    bool incGen = _refVector.isFull();
    _refVector.push_back(AtomicEntryRef());
    AttributeVector::incNumDocs();
    docId = AttributeVector::getNumDocs() - 1;
    updateUncommittedDocIdLimit(docId);
    if (incGen) {
        incGeneration();
    } else {
        reclaim_unused_memory();
    }
    return true;
}

void
TensorAttribute::checkTensorType(const vespalib::eval::Value &tensor) const
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
    auto& elem_ref = _refVector[docId];
    EntryRef oldRef(elem_ref.load_relaxed());
    elem_ref.store_release(ref);
    if (oldRef.valid()) {
        _tensorStore.holdTensor(oldRef);
    }
}

void
TensorAttribute::internal_set_tensor(DocId docid, const Value& tensor)
{
    consider_remove_from_index(docid);
    EntryRef ref = _tensorStore.store_tensor(tensor);
    assert(ref.valid());
    setTensorRef(docid, ref);
}

void
TensorAttribute::consider_remove_from_index(DocId docid)
{
    if (_index && _refVector[docid].load_relaxed().valid()) {
        _index->remove_document(docid);
    }
}

vespalib::MemoryUsage
TensorAttribute::update_stat()
{
    vespalib::MemoryUsage result = _refVector.getMemoryUsage();
    result.merge(_tensorStore.update_stat(getConfig().getCompactionStrategy()));
    result.mergeGenerationHeldBytes(getGenerationHolder().get_held_bytes());
    if (_index) {
        result.merge(_index->update_stat(getConfig().getCompactionStrategy()));
    }
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
    if (_index) {
        ObjectInserter index_inserter(object, "nearest_neighbor_index");
        _index->get_state(index_inserter);
    }
}

void
TensorAttribute::populate_address_space_usage(AddressSpaceUsage& usage) const
{
    usage.set(AddressSpaceComponents::tensor_store, _tensorStore.get_address_space_usage());
    if (!_is_dense) {
        auto stats = vespalib::SharedStringRepo::stats();
        usage.set(AddressSpaceComponents::shared_string_repo,
                  vespalib::AddressSpace(stats.max_part_usage, 0, stats.part_limit()));
    }
    if (_index) {
        _index->populate_address_space_usage(usage);
    }
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

SerializedTensorRef
TensorAttribute::get_serialized_tensor_ref(uint32_t) const
{
    notImplemented();
}

bool
TensorAttribute::supports_get_serialized_tensor_ref() const
{
    return false;
}

const vespalib::eval::ValueType &
TensorAttribute::getTensorType() const
{
    return getConfig().tensorType();
}

DistanceFunctionFactory&
TensorAttribute::distance_function_factory() const
{
    return *_distance_function_factory;

}

const NearestNeighborIndex*
TensorAttribute::nearest_neighbor_index() const
{
    return _index.get();
}

std::unique_ptr<Value>
TensorAttribute::getTensor(DocId docId) const
{
    EntryRef ref;
    if (docId < getCommittedDocIdLimit()) {
        ref = acquire_entry_ref(docId);
    }
    return _tensorStore.get_tensor(ref);
}

void
TensorAttribute::get_state(const vespalib::slime::Inserter& inserter) const
{
    auto& object = inserter.insertObject();
    populate_state(object);
}

void
TensorAttribute::clearDocs(DocId lidLow, DocId lidLimit, bool)
{
    assert(lidLow <= lidLimit);
    assert(lidLimit <= this->getNumDocs());
    for (DocId lid = lidLow; lid < lidLimit; ++lid) {
        AtomicEntryRef& atomic_ref = _refVector[lid];
        EntryRef ref = atomic_ref.load_relaxed();
        if (ref.valid()) {
            _tensorStore.holdTensor(ref);
            atomic_ref.store_release(EntryRef());
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
    if (_index) {
        _index->shrink_lid_space(committedDocIdLimit);
    }
}

uint32_t
TensorAttribute::getVersion() const
{
    return (_tensorStore.as_dense() != nullptr) ? DENSE_TENSOR_ATTRIBUTE_VERSION : TENSOR_ATTRIBUTE_VERSION;
}

bool
TensorAttribute::onLoad(vespalib::Executor* executor)
{
    TensorAttributeLoader loader(*this, getGenerationHandler(), _refVector, _tensorStore, _index.get());
    return loader.on_load(executor);
}

std::unique_ptr<AttributeSaver>
TensorAttribute::onInitSave(vespalib::stringref fileName)
{
    vespalib::GenerationHandler::Guard guard(getGenerationHandler().
                                             takeGuard());
    auto index_saver = (_index ? _index->make_saver() : std::unique_ptr<NearestNeighborIndexSaver>());
    return std::make_unique<TensorAttributeSaver>
        (std::move(guard),
         this->createAttributeHeader(fileName),
         attribute::make_entry_ref_vector_snapshot(_refVector, getCommittedDocIdLimit()),
         _tensorStore,
         std::move(index_saver));
}

void
TensorAttribute::setTensor(DocId docId, const Value& tensor)
{
    checkTensorType(tensor);
    internal_set_tensor(docId, tensor);
    if (_index) {
        _index->add_document(docId);
    }
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
    checkTensorType(tensor);
    if (_index) {
        VectorBundle vectors(tensor.cells().data, tensor.index().size(), _subspace_type);
        if (tensor_cells_are_unchanged(docid, vectors)) {
            // Don't make changes to the nearest neighbor index when the inserted tensor cells are unchanged.
            // With this optimization we avoid doing unnecessary costly work, first removing the vector point, then inserting the same point.
            return {};
        }
        return _index->prepare_add_document(docid, vectors, getGenerationHandler().takeGuard());
    }
    return {};
}

void
TensorAttribute::complete_set_tensor(DocId docid, const vespalib::eval::Value& tensor,
                                     std::unique_ptr<PrepareResult> prepare_result)
{
    if (_index && !prepare_result) {
        // The tensor cells are unchanged
        if (!_is_dense) {
            // but labels might have changed.
            EntryRef ref = _tensorStore.store_tensor(tensor);
            assert(ref.valid());
            setTensorRef(docid, ref);
        }
        return;
    }
    internal_set_tensor(docid, tensor);
    if (_index) {
        _index->complete_add_document(docid, std::move(prepare_result));
    }
}

attribute::DistanceMetric
TensorAttribute::distance_metric() const {
    return getConfig().distance_metric();
}

bool
TensorAttribute::tensor_cells_are_unchanged(DocId docid, VectorBundle vectors) const
{
    if (docid >= getCommittedDocIdLimit()) {
        return false;
    }
    auto old_vectors = get_vectors(docid);
    auto old_subspaces = old_vectors.subspaces();
    if (old_subspaces != vectors.subspaces()) {
        return false;
    }
    for (uint32_t subspace = 0; subspace < old_subspaces; ++subspace) {
        if (!_comp.equals(old_vectors.cells(subspace), vectors.cells(subspace))) {
            return false;
        }
    }
    return true;
}

}
