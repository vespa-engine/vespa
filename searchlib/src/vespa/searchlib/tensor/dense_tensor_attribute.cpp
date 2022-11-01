// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_attribute.h"
#include "nearest_neighbor_index.h"
#include "tensor_attribute_loader.h"
#include "tensor_attribute_saver.h"
#include <vespa/eval/eval/value.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/data/slime/inserter.h>

using vespalib::eval::Value;
using vespalib::slime::ObjectInserter;

namespace search::tensor {

bool
DenseTensorAttribute::tensor_is_unchanged(DocId docid, const Value& new_tensor) const
{
    auto old_tensor = extract_cells_ref(docid);
    return _comp.equals(old_tensor, new_tensor.cells());
}

void
DenseTensorAttribute::internal_set_tensor(DocId docid, const Value& tensor)
{
    consider_remove_from_index(docid);
    EntryRef ref = _denseTensorStore.store_tensor(tensor);
    setTensorRef(docid, ref);
}

void
DenseTensorAttribute::consider_remove_from_index(DocId docid)
{
    if (_index && _refVector[docid].load_relaxed().valid()) {
        _index->remove_document(docid);
    }
}

vespalib::MemoryUsage
DenseTensorAttribute::update_stat()
{
    vespalib::MemoryUsage result = TensorAttribute::update_stat();
    if (_index) {
        result.merge(_index->update_stat(getConfig().getCompactionStrategy()));
    }
    return result;
}

vespalib::MemoryUsage
DenseTensorAttribute::memory_usage() const
{
    vespalib::MemoryUsage result = TensorAttribute::memory_usage();
    if (_index) {
        result.merge(_index->memory_usage());
    }
    return result;
}

void
DenseTensorAttribute::populate_address_space_usage(AddressSpaceUsage& usage) const
{
    TensorAttribute::populate_address_space_usage(usage);
    if (_index) {
        _index->populate_address_space_usage(usage);
    }
}

DenseTensorAttribute::DenseTensorAttribute(vespalib::stringref baseFileName, const Config& cfg,
                                           const NearestNeighborIndexFactory& index_factory)
    : TensorAttribute(baseFileName, cfg, _denseTensorStore),
      _denseTensorStore(cfg.tensorType(), get_memory_allocator()),
      _comp(cfg.tensorType())
{
    if (cfg.hnsw_index_params().has_value()) {
        auto tensor_type = cfg.tensorType();
        assert(tensor_type.dimensions().size() == 1);
        assert(tensor_type.is_dense());
        size_t vector_size = tensor_type.dimensions()[0].size;
        _index = index_factory.make(*this, vector_size, tensor_type.cell_type(), cfg.hnsw_index_params().value());
    }
}


DenseTensorAttribute::~DenseTensorAttribute()
{
    getGenerationHolder().reclaim_all();
    _tensorStore.reclaim_all_memory();
}

uint32_t
DenseTensorAttribute::clearDoc(DocId docId)
{
    consider_remove_from_index(docId);
    return TensorAttribute::clearDoc(docId);
}

void
DenseTensorAttribute::setTensor(DocId docId, const Value& tensor)
{
    checkTensorType(tensor);
    internal_set_tensor(docId, tensor);
    if (_index) {
        _index->add_document(docId);
    }
}

std::unique_ptr<PrepareResult>
DenseTensorAttribute::prepare_set_tensor(DocId docid, const Value& tensor) const
{
    checkTensorType(tensor);
    if (_index) {
        if (tensor_is_unchanged(docid, tensor)) {
            // Don't make changes to the nearest neighbor index when the inserted tensor is unchanged.
            // With this optimization we avoid doing unnecessary costly work, first removing the vector point, then inserting the same point.
            return {};
        }
        return _index->prepare_add_document(docid, tensor.cells(), getGenerationHandler().takeGuard());
    }
    return {};
}

void
DenseTensorAttribute::complete_set_tensor(DocId docid, const Value& tensor,
                                          std::unique_ptr<PrepareResult> prepare_result)
{
    if (_index && !prepare_result) {
        // The tensor is unchanged.
        return;
    }
    internal_set_tensor(docid, tensor);
    if (_index) {
        _index->complete_add_document(docid, std::move(prepare_result));
    }
}

std::unique_ptr<Value>
DenseTensorAttribute::getTensor(DocId docId) const
{
    EntryRef ref;
    if (docId < getCommittedDocIdLimit()) {
        ref = acquire_entry_ref(docId);
    }
    return _denseTensorStore.get_tensor(ref);
}

vespalib::eval::TypedCells
DenseTensorAttribute::extract_cells_ref(DocId docId) const
{
    EntryRef ref;
    if (docId < getCommittedDocIdLimit()) {
        ref = acquire_entry_ref(docId);
    }
    return _denseTensorStore.get_typed_cells(ref);
}

void
DenseTensorAttribute::onCommit()
{
    TensorAttribute::onCommit();
    if (_index) {
        if (_index->consider_compact(getConfig().getCompactionStrategy())) {
            incGeneration();
            updateStat(true);
        }
    }
}

void
DenseTensorAttribute::before_inc_generation(generation_t current_gen)
{
    TensorAttribute::before_inc_generation(current_gen);
    if (_index) {
        _index->assign_generation(current_gen);
    }
}

void
DenseTensorAttribute::reclaim_memory(generation_t oldest_used_gen)
{
    TensorAttribute::reclaim_memory(oldest_used_gen);
    if (_index) {
        _index->reclaim_memory(oldest_used_gen);
    }
}

void
DenseTensorAttribute::get_state(const vespalib::slime::Inserter& inserter) const
{
    auto& object = inserter.insertObject();
    populate_state(object);
    if (_index) {
        ObjectInserter index_inserter(object, "nearest_neighbor_index");
        _index->get_state(index_inserter);
    }
}

void
DenseTensorAttribute::onShrinkLidSpace()
{
    TensorAttribute::onShrinkLidSpace();
    if (_index) {
        _index->shrink_lid_space(getCommittedDocIdLimit());
    }
}

vespalib::eval::TypedCells
DenseTensorAttribute::get_vector(uint32_t docid) const
{
    EntryRef ref = acquire_entry_ref(docid);
    return _denseTensorStore.get_typed_cells(ref);
}

}
