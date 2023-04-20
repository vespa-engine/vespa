// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/imported_attribute_vector_read_guard.h>
#include "i_tensor_attribute.h"

namespace search::attribute {
class ImportedAttributeVector;
}

namespace search::tensor {

/**
 * Short lived attribute vector for imported tensor attributes.
 *
 * Extra information for direct lid to target lid mapping with
 * boundary check is setup during construction.
 */
class ImportedTensorAttributeVectorReadGuard : public attribute::ImportedAttributeVectorReadGuard,
                                               public ITensorAttribute
{
    using ReferenceAttribute = attribute::ReferenceAttribute;
    using BitVectorSearchCache = attribute::BitVectorSearchCache;
    const ITensorAttribute &_target_tensor_attribute;
public:
    ImportedTensorAttributeVectorReadGuard(std::shared_ptr<MetaStoreReadGuard> targetMetaStoreReadGuard,
                                           const attribute::ImportedAttributeVector &imported_attribute,
                                           bool stableEnumGuard);
    ~ImportedTensorAttributeVectorReadGuard();

    const ITensorAttribute *asTensorAttribute() const override;

    std::unique_ptr<vespalib::eval::Value> getTensor(uint32_t docId) const override;
    std::unique_ptr<vespalib::eval::Value> getEmptyTensor() const override;
    vespalib::eval::TypedCells extract_cells_ref(uint32_t docid) const override;
    const vespalib::eval::Value& get_tensor_ref(uint32_t docid) const override;
    SerializedTensorRef get_serialized_tensor_ref(uint32_t docid) const override;
    bool supports_extract_cells_ref() const override { return _target_tensor_attribute.supports_extract_cells_ref(); }
    bool supports_get_tensor_ref() const override { return _target_tensor_attribute.supports_get_tensor_ref(); }
    DistanceFunctionFactory& distance_function_factory() const override {
        return _target_tensor_attribute.distance_function_factory();
    }
    DistanceMetric distance_metric() const override { return _target_tensor_attribute.distance_metric(); }
    bool supports_get_serialized_tensor_ref() const override;
    uint32_t get_num_docs() const override { return getNumDocs(); }

    vespalib::eval::TypedCells get_vector(uint32_t docid, uint32_t subspace) const override;
    VectorBundle get_vectors(uint32_t docid) const override;

    const vespalib::eval::ValueType &getTensorType() const override;
    void get_state(const vespalib::slime::Inserter& inserter) const override;
};

}
