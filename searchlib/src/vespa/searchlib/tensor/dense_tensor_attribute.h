// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "default_nearest_neighbor_index_factory.h"
#include "dense_tensor_store.h"
#include "tensor_attribute.h"
#include <memory>

namespace search::tensor {

class NearestNeighborIndex;

/**
 * Attribute vector class used to store dense tensors for all
 * documents in memory.
 */
class DenseTensorAttribute : public TensorAttribute {
private:
    DenseTensorStore _denseTensorStore;

public:
    DenseTensorAttribute(vespalib::stringref baseFileName, const Config& cfg,
                         const NearestNeighborIndexFactory& index_factory = DefaultNearestNeighborIndexFactory());
    ~DenseTensorAttribute() override;
    // Implements AttributeVector and ITensorAttribute
    vespalib::eval::TypedCells extract_cells_ref(DocId docId) const override;
    bool supports_extract_cells_ref() const override { return true; }
    void get_state(const vespalib::slime::Inserter& inserter) const override;

    // Implements DocVectorAccess
    vespalib::eval::TypedCells get_vector(uint32_t docid, uint32_t subspace) const override;
    VectorBundle get_vectors(uint32_t docid) const override;
};

}
