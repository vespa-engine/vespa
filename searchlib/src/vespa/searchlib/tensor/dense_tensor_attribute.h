// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "default_nearest_neighbor_index_factory.h"
#include "dense_tensor_store.h"
#include "doc_vector_access.h"
#include "tensor_attribute.h"
#include <memory>

namespace vespalib::tensor { class MutableDenseTensorView; }

namespace search::tensor {

class NearestNeighborIndex;

/**
 * Attribute vector class used to store dense tensors for all
 * documents in memory.
 */
class DenseTensorAttribute : public TensorAttribute, public DocVectorAccess {
private:
    DenseTensorStore _denseTensorStore;
    std::unique_ptr<NearestNeighborIndex> _index;

    void internal_set_tensor(DocId docid, const Tensor& tensor);
    void consider_remove_from_index(DocId docid);
    vespalib::MemoryUsage memory_usage() const override;

public:
    DenseTensorAttribute(vespalib::stringref baseFileName, const Config& cfg,
                         const NearestNeighborIndexFactory& index_factory = DefaultNearestNeighborIndexFactory());
    virtual ~DenseTensorAttribute();
    // Implements AttributeVector and ITensorAttribute
    uint32_t clearDoc(DocId docId) override;
    void setTensor(DocId docId, const Tensor &tensor) override;
    std::unique_ptr<PrepareResult> prepare_set_tensor(DocId docid, const Tensor& tensor) const override;
    void complete_set_tensor(DocId docid, const Tensor& tensor, std::unique_ptr<PrepareResult> prepare_result) override;
    std::unique_ptr<Tensor> getTensor(DocId docId) const override;
    void extract_dense_view(DocId docId, vespalib::tensor::MutableDenseTensorView &tensor) const override;
    bool supports_extract_dense_view() const override { return true; }
    bool onLoad() override;
    std::unique_ptr<AttributeSaver> onInitSave(vespalib::stringref fileName) override;
    void compactWorst() override;
    uint32_t getVersion() const override;
    void onGenerationChange(generation_t next_gen) override;
    void removeOldGenerations(generation_t first_used_gen) override;
    void get_state(const vespalib::slime::Inserter& inserter) const override;

    // Implements DocVectorAccess
    vespalib::eval::TypedCells get_vector(uint32_t docid) const override;

    const NearestNeighborIndex* nearest_neighbor_index() const { return _index.get(); }
};

}
