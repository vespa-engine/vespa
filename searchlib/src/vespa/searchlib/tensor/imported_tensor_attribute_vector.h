// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/imported_attribute_vector.h>

namespace search::tensor {

class ITensorAttribute;

/**
 * Attribute vector for imported tensor attributes.
 */
class ImportedTensorAttributeVector : public attribute::ImportedAttributeVector
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

    virtual std::unique_ptr<attribute::AttributeReadGuard> makeReadGuard(bool stableEnumGuard) const override;
};

}
