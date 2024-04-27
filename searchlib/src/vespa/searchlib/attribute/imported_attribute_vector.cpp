// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "imported_attribute_vector.h"
#include "imported_attribute_vector_read_guard.h"
#include "imported_search_context.h"
#include <vespa/vespalib/util/memoryusage.h>

namespace search::attribute {

ImportedAttributeVector::ImportedAttributeVector(
            vespalib::stringref name,
            std::shared_ptr<ReferenceAttribute> reference_attribute,
            std::shared_ptr<IDocumentMetaStoreContext> document_meta_store,
            std::shared_ptr<ReadableAttributeVector> target_attribute,
            std::shared_ptr<const IDocumentMetaStoreContext> target_document_meta_store,
            bool use_search_cache)
    : _name(name),
      _reference_attribute(std::move(reference_attribute)),
      _document_meta_store(std::move(document_meta_store)),
      _target_attribute(std::move(target_attribute)),
      _target_document_meta_store(std::move(target_document_meta_store)),
      _search_cache(use_search_cache ? std::make_shared<BitVectorSearchCache>() :
                    std::shared_ptr<BitVectorSearchCache>())
{
}

ImportedAttributeVector::ImportedAttributeVector(vespalib::stringref name,
                                                 std::shared_ptr<ReferenceAttribute> reference_attribute,
                                                 std::shared_ptr<IDocumentMetaStoreContext> document_meta_store,
                                                 std::shared_ptr<ReadableAttributeVector> target_attribute,
                                                 std::shared_ptr<const IDocumentMetaStoreContext> target_document_meta_store,
                                                 std::shared_ptr<BitVectorSearchCache> search_cache)
    : _name(name),
      _reference_attribute(std::move(reference_attribute)),
      _document_meta_store(std::move(document_meta_store)),
      _target_attribute(std::move(target_attribute)),
      _target_document_meta_store(std::move(target_document_meta_store)),
      _search_cache(std::move(search_cache))
{
}

ImportedAttributeVector::~ImportedAttributeVector() = default;

std::unique_ptr<AttributeReadGuard>
ImportedAttributeVector::makeReadGuard(bool stableEnumGuard) const
{
    return makeReadGuard(_target_document_meta_store->getReadGuard(), stableEnumGuard);
}

std::unique_ptr<AttributeReadGuard>
ImportedAttributeVector::makeReadGuard(std::shared_ptr<MetaStoreReadGuard> targetMetaStoreReadGuard,  bool stableEnumGuard) const
{
    return std::make_unique<ImportedAttributeVectorReadGuard>(std::move(targetMetaStoreReadGuard), *this, stableEnumGuard);
}

void ImportedAttributeVector::clearSearchCache() {
    if (_search_cache) {
        _search_cache->clear();
    }
}

vespalib::MemoryUsage
ImportedAttributeVector::get_memory_usage() const
{
    constexpr auto self_memory_usage = sizeof(ImportedAttributeVector);
    vespalib::MemoryUsage result(self_memory_usage, self_memory_usage, 0, 0);
    if (_search_cache) {
        result.merge(_search_cache->get_memory_usage());
    }
    return result;
}

}
