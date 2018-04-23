// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "imported_tensor_attribute_vector.h"
#include "imported_tensor_attribute_vector_read_guard.h"
#include <vespa/eval/tensor/tensor.h>

namespace search::tensor {

using vespalib::tensor::Tensor;

ImportedTensorAttributeVector::ImportedTensorAttributeVector(vespalib::stringref name,
                                                             std::shared_ptr<ReferenceAttribute> reference_attribute,
                                                             std::shared_ptr<IDocumentMetaStoreContext> document_meta_store,
                                                             std::shared_ptr<attribute::ReadableAttributeVector> target_attribute,
                                                             std::shared_ptr<const IDocumentMetaStoreContext> target_document_meta_store,
                                                             bool use_search_cache)
    : ImportedAttributeVector(name, std::move(reference_attribute),
                              std::move(document_meta_store),
                              std::move(target_attribute),
                              std::move(target_document_meta_store),
                              use_search_cache)
{
}

ImportedTensorAttributeVector::ImportedTensorAttributeVector(vespalib::stringref name,
                                                             std::shared_ptr<ReferenceAttribute> reference_attribute,
                                                             std::shared_ptr<IDocumentMetaStoreContext> document_meta_store,
                                                             std::shared_ptr<attribute::ReadableAttributeVector> target_attribute,
                                                             std::shared_ptr<const IDocumentMetaStoreContext> target_document_meta_store,
                                                             std::shared_ptr<BitVectorSearchCache> search_cache)
    : ImportedAttributeVector(name, std::move(reference_attribute),
                              std::move(document_meta_store),
                              std::move(target_attribute),
                              std::move(target_document_meta_store),
                              std::move(search_cache))
{
}

ImportedTensorAttributeVector::~ImportedTensorAttributeVector()
{
}

std::unique_ptr<attribute::AttributeReadGuard>
ImportedTensorAttributeVector::makeReadGuard(bool stableEnumGuard) const
{
    return std::make_unique<ImportedTensorAttributeVectorReadGuard>(*this, stableEnumGuard);
}

}
