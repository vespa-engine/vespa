// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
 * Extra information for direct lid to referenced lid mapping with
 * boundary check is setup during construction.
 */
class ImportedTensorAttributeVectorReadGuard : public attribute::ImportedAttributeVectorReadGuard,
                                               public ITensorAttribute
{
    using ReferenceAttribute = attribute::ReferenceAttribute;
    using BitVectorSearchCache = attribute::BitVectorSearchCache;
    const ITensorAttribute &_target_tensor_attribute;
public:
    ImportedTensorAttributeVectorReadGuard(const attribute::ImportedAttributeVector &imported_attribute,
                                           bool stableEnumGuard);
    ~ImportedTensorAttributeVectorReadGuard();

    const ITensorAttribute *asTensorAttribute() const override;

    virtual std::unique_ptr<Tensor> getTensor(uint32_t docId) const override;
    virtual std::unique_ptr<Tensor> getEmptyTensor() const override;
    virtual void getTensor(uint32_t docId, vespalib::tensor::MutableDenseTensorView &tensor) const override;
    virtual vespalib::eval::ValueType getTensorType() const override;
};

}
