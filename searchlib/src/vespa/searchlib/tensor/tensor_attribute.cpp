// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_attribute.h"
#include "nearest_neighbor_index.h"
#include "nearest_neighbor_index_factory.h"
#include "nearest_neighbor_index_saver.h"
#include "serialized_tensor_ref.h"
#include "tensor_attribute_constants.h"
#include "tensor_attribute_explorer.h"
#include "tensor_attribute_flags.h"
#include "tensor_attribute_loader.h"
#include "tensor_attribute_saver.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/searchlib/attribute/address_space_components.h>
#include <vespa/searchlib/util/file_settings.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/datastore/i_compaction_context.h>
#include <vespa/vespalib/util/shared_string_repo.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <algorithm>

using document::TensorDataType;
using document::TensorUpdate;
using document::WrongTensorTypeException;
using search::AddressSpaceComponents;
using vespalib::Generation;
using vespalib::GenerationGuard;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;
using vespalib::eval::ValueType;

namespace search::tensor {

namespace {

Value::UP
createEmptyTensor(const ValueType &type)
{
    const auto &factory = FastValueBuilderFactory::get();
    TensorSpec empty_spec(type.to_spec());
    return vespalib::eval::value_from_spec(empty_spec, factory);
}

std::string makeWrongTensorTypeMsg(const ValueType &fieldTensorType, const ValueType &tensorType)
{
    return vespalib::make_string("Field tensor type is '%s' but other tensor type is '%s'",
                                 fieldTensorType.to_spec().c_str(),
                                 tensorType.to_spec().c_str());
}

}

TensorAttribute::TensorAttribute(std::string_view name, const Config &cfg, TensorStore &tensorStore, const NearestNeighborIndexFactory& index_factory)
    : NotImplementedAttribute(name, cfg),
      _refVector(cfg.getGrowStrategy(), getGenerationHolder()),
      _tensorStore(tensorStore),
      _distance_function_factory(make_distance_function_factory(cfg.distance_metric(), cfg.tensorType().cell_type())),
      _index(),
      _is_dense(cfg.tensorType().is_dense()),
      _emptyTensor(createEmptyTensor(cfg.tensorType())),
      _compactGeneration(0),
      _subspace_type(cfg.tensorType()),
      _comp(cfg.tensorType()),
      _memory_usage_empty(0),
      _memory_usage_at_save_start(0),
      _size_on_disk_factor(1.0)
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
            context->compact(std::span<AtomicEntryRef>(&_refVector[0], _refVector.size()));
        }
        _compactGeneration = getCurrentGeneration();
        incGeneration();
        updateStat(CommitParam::UpdateStats::FORCE);
    }
    if (_index) {
        if (_index->consider_compact(getConfig().getCompactionStrategy())) {
            incGeneration();
            updateStat(CommitParam::UpdateStats::FORCE);
        }
    }
}

void
TensorAttribute::onUpdateStat(CommitParam::UpdateStats updateStats)
{
    if (updateStats == CommitParam::UpdateStats::SKIP) {
        return;
    }
    if (updateStats == CommitParam::UpdateStats::SIZES_ONLY) {
        this->updateSizes(_refVector.size(), _refVector.size());
        return;
    }
    vespalib::MemoryUsage total = update_stat();
    this->updateStatistics(_refVector.size(),
                           _refVector.size(),
                           total.allocatedBytes(),
                           total.usedBytes(),
                           total.deadBytes(),
                           total.allocatedBytesOnHold());
}

void
TensorAttribute::reclaim_memory(Generation oldest_used_gen)
{
    _tensorStore.reclaim_memory(oldest_used_gen);
    getGenerationHolder().reclaim(oldest_used_gen);
}

void
TensorAttribute::before_inc_generation(Generation current_gen)
{
    getGenerationHolder().assign_generation(current_gen);
    _tensorStore.assign_generation(current_gen);
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

std::unique_ptr<vespalib::StateExplorer>
TensorAttribute::make_state_explorer() const
{
    return std::make_unique<TensorAttributeExplorer>(_compactGeneration.value(), _refVector, _tensorStore, _index.get());
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
TensorAttribute::onInitSave(std::string_view fileName)
{
    set_memory_usage_at_save_start(getStatus().get_used_minus_dead_and_onhold());
    auto guard(getGenerationHandler().takeGuard());
    auto header = this->createAttributeHeader(fileName);
    auto index_saver = (_index ? _index->make_saver(header.get_extra_tags()) : std::unique_ptr<NearestNeighborIndexSaver>());
    return std::make_unique<TensorAttributeSaver>
        (std::move(guard),
         std::move(header),
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
        auto guard = getGenerationHandler().takeGuard();
        VectorBundle vectors(tensor.cells().data, tensor.index().size(), _subspace_type);
        if (tensor_cells_are_unchanged(docid, vectors)) {
            // Don't make changes to the nearest neighbor index when the inserted tensor cells are unchanged.
            // With this optimization we avoid doing unnecessary costly work, first removing the vector point, then inserting the same point.
            return {};
        }
        return _index->prepare_add_document(docid, vectors, std::move(guard));
    }
    return {};
}

void
TensorAttribute::complete_set_tensor(DocId docid, const vespalib::eval::Value& tensor,
                                     std::unique_ptr<PrepareResult> prepare_result)
{
    if (_index && !prepare_result) {
        VectorBundle vectors(tensor.cells().data, tensor.index().size(), _subspace_type);
        if (tensor_cells_are_unchanged(docid, vectors)) {
            // The tensor cells are unchanged
            if (!_is_dense) {
                // but labels might have changed.
                EntryRef ref = _tensorStore.store_tensor(tensor);
                assert(ref.valid());
                setTensorRef(docid, ref);
            }
            return;
        }
    }
    internal_set_tensor(docid, tensor);
    if (_index) {
        if (prepare_result) {
            _index->complete_add_document(docid, std::move(prepare_result));
        } else {
            _index->add_document(docid);
        }
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

void
TensorAttribute::setup_memory_usage_empty()
{
    updateStat(CommitParam::UpdateStats::FORCE);
    _memory_usage_empty = getStatus().get_used_minus_dead_and_onhold();
    _memory_usage_at_save_start = _memory_usage_empty;
}

void
TensorAttribute::set_memory_usage_at_save_start(uint64_t memory_usage) noexcept
{
    _memory_usage_at_save_start = std::max(memory_usage, _memory_usage_empty);
}

void
TensorAttribute::set_size_on_disk(uint64_t value) noexcept
{
    AttributeVector::set_size_on_disk(value);
    uint64_t headerSize = FileSettings::DIRECTIO_ALIGNMENT;
    double size_on_disk_factor = 1.0;
    auto dynamic_memory_usage = _memory_usage_at_save_start - _memory_usage_empty;
    if (dynamic_memory_usage >= 40_Ki) {
        size_on_disk_factor = static_cast<double>(value - headerSize) / dynamic_memory_usage;
    }
    auto clamped_size_on_disk_factor = std::clamp<double>(size_on_disk_factor, 0.1, 10.0);
    _size_on_disk_factor.store(clamped_size_on_disk_factor, std::memory_order_relaxed);
}

uint64_t
TensorAttribute::getEstimatedSaveByteSize() const
{
    const Status &status = getStatus();
    uint64_t headerSize = FileSettings::DIRECTIO_ALIGNMENT;
    uint64_t dynamic_memory_usage = std::max(status.get_used_minus_dead_and_onhold() - _memory_usage_empty, static_cast<uint64_t>(4_Ki));
    double size_on_disk_factor = _size_on_disk_factor.load(std::memory_order_relaxed);
    /*
     * A tensor label is stored in memory as a vespalib::string_id (4 bytes long) that references an entry in a
     * shared string repo. The serialized format on disk contains the full tensor label string. Thus, tensors with
     * long tensor labels will use more space on disk than in memory.
     */
    double estimate = size_on_disk_factor * dynamic_memory_usage + headerSize;
    return estimate;
}

void
TensorAttribute::incGeneration()
{
    auto& generation_handler = getGenerationHandler();
    auto current_gen = generation_handler.getCurrentGeneration();
    before_inc_generation(current_gen);
    if constexpr (!TensorAttributeFlags::use_nearest_neighbor_index_generation_manager) {
        if (_index) {
            _index->assign_generation(current_gen);
        }
    }
    generation_handler.incGeneration();
    if constexpr (TensorAttributeFlags::use_nearest_neighbor_index_generation_manager) {
        if (_index) {
            _index->inc_generation();
        }
    }
    // Remove old data on hold lists that can no longer be reached by readers
    reclaim_unused_memory();
}

void
TensorAttribute::reclaim_unused_memory()
{
    auto& generation_handler = getGenerationHandler();
    generation_handler.update_oldest_used_generation();
    auto oldest_used_gen = generation_handler.get_oldest_used_generation();
    reclaim_memory(oldest_used_gen);
    if (_index) {
        if constexpr (TensorAttributeFlags::use_nearest_neighbor_index_generation_manager) {
            _index->reclaim_unused_memory();
        } else {
            _index->reclaim_memory(oldest_used_gen);
        }
    }
}

}
