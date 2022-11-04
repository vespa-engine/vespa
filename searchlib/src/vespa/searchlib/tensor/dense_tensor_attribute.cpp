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
        VectorBundle vectors(tensor.cells().data, tensor.index().size(), _denseTensorStore.get_subspace_type());
        return _index->prepare_add_document(docid, vectors, getGenerationHandler().takeGuard());
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
DenseTensorAttribute::get_state(const vespalib::slime::Inserter& inserter) const
{
    auto& object = inserter.insertObject();
    populate_state(object);
    if (_index) {
        ObjectInserter index_inserter(object, "nearest_neighbor_index");
        _index->get_state(index_inserter);
    }
}

vespalib::eval::TypedCells
DenseTensorAttribute::get_vector(uint32_t docid, uint32_t subspace) const
{
    EntryRef ref = (subspace == 0) ? acquire_entry_ref(docid) : EntryRef();
    return _denseTensorStore.get_typed_cells(ref);
}

VectorBundle
DenseTensorAttribute::get_vectors(uint32_t docid) const
{
    EntryRef ref = acquire_entry_ref(docid);
    return _denseTensorStore.get_vectors(ref);
}

}
