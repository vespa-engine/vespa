// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "imported_attribute_vector_factory.h"
#include "imported_attribute_vector.h"
#include "attribute_read_guard.h"
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchlib/tensor/imported_tensor_attribute_vector.h>

namespace search::attribute {

using search::tensor::ImportedTensorAttributeVector;

namespace {

BasicType::Type getBasicType(const std::shared_ptr<attribute::ReadableAttributeVector> &attr)
{
    if (attr) {
        auto readGuard = attr->makeReadGuard(false);
        return readGuard->attribute()->getBasicType();
    } else {
        return BasicType::Type::NONE;
    }
}

}

std::shared_ptr<ImportedAttributeVector>
ImportedAttributeVectorFactory::create(vespalib::stringref name,
                                       std::shared_ptr<ReferenceAttribute> reference_attribute,
                                       std::shared_ptr<IDocumentMetaStoreContext> document_meta_store,
                                       std::shared_ptr<ReadableAttributeVector> target_attribute,
                                       std::shared_ptr<const IDocumentMetaStoreContext> target_document_meta_store,
                                       bool use_search_cache)
{
    switch (getBasicType(target_attribute)) {
    case BasicType::Type::TENSOR:
        return std::make_shared<ImportedTensorAttributeVector>(name, std::move(reference_attribute), std::move(document_meta_store), std::move(target_attribute), std::move(target_document_meta_store), use_search_cache);
    default:
        return std::make_shared<ImportedAttributeVector>(name, std::move(reference_attribute), std::move(document_meta_store), std::move(target_attribute), std::move(target_document_meta_store), use_search_cache);
    }
}


std::shared_ptr<ImportedAttributeVector>
ImportedAttributeVectorFactory::create(vespalib::stringref name,
                                       std::shared_ptr<ReferenceAttribute> reference_attribute,
                                       std::shared_ptr<IDocumentMetaStoreContext> document_meta_store,
                                       std::shared_ptr<ReadableAttributeVector> target_attribute,
                                       std::shared_ptr<const IDocumentMetaStoreContext> target_document_meta_store,
                                       std::shared_ptr<BitVectorSearchCache> search_cache)
{
    switch (getBasicType(target_attribute)) {
    case BasicType::Type::TENSOR:
        return std::make_shared<ImportedTensorAttributeVector>(name, std::move(reference_attribute), std::move(document_meta_store), std::move(target_attribute), std::move(target_document_meta_store), std::move(search_cache));
    default:
        return std::make_shared<ImportedAttributeVector>(name, std::move(reference_attribute), std::move(document_meta_store), std::move(target_attribute), std::move(target_document_meta_store), std::move(search_cache));
    }
}

} // search::attribute
