// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_attribute.h"
#include "default_nearest_neighbor_index_factory.h"
#include "direct_tensor_store.h"

namespace vespalib::eval { struct Value; }

namespace search::tensor {

class DirectTensorAttribute final : public TensorAttribute
{
    DirectTensorStore _direct_store;

    void set_tensor(DocId docId, std::unique_ptr<vespalib::eval::Value> tensor);
public:
    DirectTensorAttribute(vespalib::stringref baseFileName, const Config &cfg, const NearestNeighborIndexFactory& index_factory = DefaultNearestNeighborIndexFactory());
    ~DirectTensorAttribute() override;
    void setTensor(DocId docId, const vespalib::eval::Value &tensor) override;
    void update_tensor(DocId docId,
                       const document::TensorUpdate &update,
                       bool create_empty_if_non_existing) override;
    const vespalib::eval::Value &get_tensor_ref(DocId docId) const override;
    bool supports_get_tensor_ref() const override { return true; }

    // Implements DocVectorAccess
    vespalib::eval::TypedCells get_vector(uint32_t docid, uint32_t subspace) const override;
    VectorBundle get_vectors(uint32_t docid) const override;
};

}  // namespace search::tensor
