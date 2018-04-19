// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/imported_attribute_vector.h>
#include "i_tensor_attribute.h"

namespace search::tensor {

/**
 * Attribute vector for imported tensor attributes.
 */
class ImportedTensorAttributeVector : public attribute::ImportedAttributeVector,
                                      public ITensorAttribute
{
    using ReferenceAttribute = attribute::ReferenceAttribute;
    using BitVectorSearchCache = attribute::BitVectorSearchCache;
    const ITensorAttribute &_target_tensor_attribute;
public:
    ImportedTensorAttributeVector(vespalib::stringref name,
                                  std::shared_ptr<ReferenceAttribute> reference_attribute,
                                  std::shared_ptr<AttributeVector> target_attribute,
                                  std::shared_ptr<IDocumentMetaStoreContext> document_meta_store,
                                  bool use_search_cache);
    ImportedTensorAttributeVector(vespalib::stringref name,
                                  std::shared_ptr<ReferenceAttribute> reference_attribute,
                                  std::shared_ptr<AttributeVector> target_attribute,
                                  std::shared_ptr<IDocumentMetaStoreContext> document_meta_store,
                                  std::shared_ptr<BitVectorSearchCache> search_cache);
    ~ImportedTensorAttributeVector();

    virtual std::unique_ptr<attribute::IAttributeVector> makeReadGuard(bool stableEnumGuard) const override;
    virtual std::unique_ptr<Tensor> getTensor(uint32_t docId) const override;
    virtual std::unique_ptr<Tensor> getEmptyTensor() const override;
    virtual void getTensor(uint32_t docId, vespalib::tensor::MutableDenseTensorView &tensor) const override;
    virtual vespalib::eval::ValueType getTensorType() const override;
};

}  // namespace search::tensor
