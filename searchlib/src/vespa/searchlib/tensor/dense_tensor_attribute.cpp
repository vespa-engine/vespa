// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_attribute.h"
#include <vespa/searchcommon/attribute/config.h>

namespace search::tensor {

DenseTensorAttribute::DenseTensorAttribute(vespalib::stringref baseFileName, const Config& cfg,
                                           const NearestNeighborIndexFactory& index_factory)
    : TensorAttribute(baseFileName, cfg, _denseTensorStore, index_factory),
      _denseTensorStore(cfg.tensorType(), get_memory_allocator())
{
}


DenseTensorAttribute::~DenseTensorAttribute()
{
    getGenerationHolder().reclaim_all();
    _tensorStore.reclaim_all_memory();
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
