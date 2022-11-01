// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "default_nearest_neighbor_index_factory.h"
#include "dense_tensor_store.h"
#include "doc_vector_access.h"
#include "tensor_attribute.h"
#include "typed_cells_comparator.h"
#include <memory>

namespace search::tensor {

class NearestNeighborIndex;

/**
 * Attribute vector class used to store dense tensors for all
 * documents in memory.
 */
class DenseTensorAttribute : public TensorAttribute, public DocVectorAccess {
private:
    DenseTensorStore _denseTensorStore;
    TypedCellsComparator _comp;

    bool tensor_is_unchanged(DocId docid, const vespalib::eval::Value& new_tensor) const;
public:
    DenseTensorAttribute(vespalib::stringref baseFileName, const Config& cfg,
                         const NearestNeighborIndexFactory& index_factory = DefaultNearestNeighborIndexFactory());
    ~DenseTensorAttribute() override;
    // Implements AttributeVector and ITensorAttribute
    std::unique_ptr<PrepareResult> prepare_set_tensor(DocId docid, const vespalib::eval::Value& tensor) const override;
    void complete_set_tensor(DocId docid, const vespalib::eval::Value& tensor, std::unique_ptr<PrepareResult> prepare_result) override;
    vespalib::eval::TypedCells extract_cells_ref(DocId docId) const override;
    bool supports_extract_cells_ref() const override { return true; }
    void get_state(const vespalib::slime::Inserter& inserter) const override;

    // Implements DocVectorAccess
    vespalib::eval::TypedCells get_vector(uint32_t docid) const override;
};

}
