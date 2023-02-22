// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_attribute.h"
#include "default_nearest_neighbor_index_factory.h"
#include "tensor_buffer_store.h"

namespace search::tensor {

/**
 * Attribute vector class storing serialized tensors for all documents in memory.
 *
 * When fetching a tensor with getTensor(docId) the returned Value
 * will have a FastValueIndex (constructed on the fly) for its sparse
 * mapping, but refer to a common type, while cells() will refer to
 * memory in the serialized store without copying.
 *
 */
class SerializedFastValueAttribute : public TensorAttribute {
    TensorBufferStore _tensorBufferStore; // data store for serialized tensors
public:
    SerializedFastValueAttribute(vespalib::stringref baseFileName, const Config &cfg, const NearestNeighborIndexFactory& index_factory = DefaultNearestNeighborIndexFactory());
    ~SerializedFastValueAttribute() override;

    SerializedTensorRef get_serialized_tensor_ref(uint32_t docid) const override;
    bool supports_get_serialized_tensor_ref() const override;

    // Implements DocVectorAccess
    vespalib::eval::TypedCells get_vector(uint32_t docid, uint32_t subspace) const override;
    VectorBundle get_vectors(uint32_t docid) const override;
};

}
