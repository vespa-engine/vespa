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

public:
    DenseTensorAttribute(vespalib::stringref baseFileName, const Config& cfg,
                         const NearestNeighborIndexFactory& index_factory = DefaultNearestNeighborIndexFactory());
    virtual ~DenseTensorAttribute();
    // Implements TensorAttribute
    virtual void setTensor(DocId docId, const Tensor &tensor) override;
    virtual std::unique_ptr<Tensor> getTensor(DocId docId) const override;
    virtual void getTensor(DocId docId, vespalib::tensor::MutableDenseTensorView &tensor) const override;
    virtual bool onLoad() override;
    virtual std::unique_ptr<AttributeSaver> onInitSave(vespalib::stringref fileName) override;
    virtual void compactWorst() override;
    virtual uint32_t getVersion() const override;

    // Implements DocVectorAccess
    vespalib::tensor::TypedCells get_vector(uint32_t docid) const override;

    const NearestNeighborIndex* nearest_neighbor_index() const { return _index.get(); }
};

}
