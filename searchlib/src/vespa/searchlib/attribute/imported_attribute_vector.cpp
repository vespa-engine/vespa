// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "imported_attribute_vector.h"
#include "imported_attribute_vector_read_guard.h"
#include "imported_search_context.h"
#include "bitvector_search_cache.h"
#include <vespa/searchlib/query/queryterm.h>
#include <vespa/vespalib/util/exceptions.h>

namespace search {
namespace attribute {

ImportedAttributeVector::ImportedAttributeVector(
            vespalib::stringref name,
            std::shared_ptr<ReferenceAttribute> reference_attribute,
            std::shared_ptr<AttributeVector> target_attribute,
            bool use_search_cache)
    : _name(name),
      _reference_attribute(std::move(reference_attribute)),
      _target_attribute(std::move(target_attribute)),
      _search_cache(use_search_cache ? std::make_shared<BitVectorSearchCache>() :
                    std::shared_ptr<BitVectorSearchCache>())
{
}

ImportedAttributeVector::ImportedAttributeVector(vespalib::stringref name,
                                                 std::shared_ptr<ReferenceAttribute> reference_attribute,
                                                 std::shared_ptr<AttributeVector> target_attribute,
                                                 std::shared_ptr<BitVectorSearchCache> search_cache)
    : _name(name),
      _reference_attribute(std::move(reference_attribute)),
      _target_attribute(std::move(target_attribute)),
      _search_cache(std::move(search_cache))
{
}

ImportedAttributeVector::~ImportedAttributeVector() {
}

std::unique_ptr<ImportedAttributeVector>
ImportedAttributeVector::makeReadGuard(bool stableEnumGuard) const
{
    return std::make_unique<ImportedAttributeVectorReadGuard>
        (getName(), getReferenceAttribute(), getTargetAttribute(), getSearchCache(), stableEnumGuard);
}

const vespalib::string& search::attribute::ImportedAttributeVector::getName() const {
    return _name;
}

uint32_t ImportedAttributeVector::getNumDocs() const {
    return _reference_attribute->getNumDocs();
}

uint32_t ImportedAttributeVector::getValueCount(uint32_t doc) const {
    return _target_attribute->getValueCount(_reference_attribute->getReferencedLid(doc));
}

uint32_t ImportedAttributeVector::getMaxValueCount() const {
    return _target_attribute->getMaxValueCount();
}

IAttributeVector::largeint_t ImportedAttributeVector::getInt(DocId doc) const {
    return _target_attribute->getInt(_reference_attribute->getReferencedLid(doc));
}

double ImportedAttributeVector::getFloat(DocId doc) const {
    return _target_attribute->getFloat(_reference_attribute->getReferencedLid(doc));
}

const char *ImportedAttributeVector::getString(DocId doc, char *buffer, size_t sz) const {
    return _target_attribute->getString(_reference_attribute->getReferencedLid(doc), buffer, sz);
}

IAttributeVector::EnumHandle ImportedAttributeVector::getEnum(DocId doc) const {
    return _target_attribute->getEnum(_reference_attribute->getReferencedLid(doc));
}

uint32_t ImportedAttributeVector::get(DocId docId, largeint_t *buffer, uint32_t sz) const {
    return _target_attribute->get(_reference_attribute->getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVector::get(DocId docId, double *buffer, uint32_t sz) const {
    return _target_attribute->get(_reference_attribute->getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVector::get(DocId docId, const char **buffer, uint32_t sz) const {
    return _target_attribute->get(_reference_attribute->getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVector::get(DocId docId, EnumHandle *buffer, uint32_t sz) const {
    return _target_attribute->get(_reference_attribute->getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVector::get(DocId docId, WeightedInt *buffer, uint32_t sz) const {
    return _target_attribute->get(_reference_attribute->getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVector::get(DocId docId, WeightedFloat *buffer, uint32_t sz) const {
    return _target_attribute->get(_reference_attribute->getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVector::get(DocId docId, WeightedString *buffer, uint32_t sz) const {
    return _target_attribute->get(_reference_attribute->getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVector::get(DocId docId, WeightedConstChar *buffer, uint32_t sz) const {
    return _target_attribute->get(_reference_attribute->getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVector::get(DocId docId, WeightedEnum *buffer, uint32_t sz) const {
    return _target_attribute->get(_reference_attribute->getReferencedLid(docId), buffer, sz);
}

bool ImportedAttributeVector::findEnum(const char *value, EnumHandle &e) const {
    return _target_attribute->findEnum(value, e);
}

const char * ImportedAttributeVector::getStringFromEnum(EnumHandle e) const {
    return _target_attribute->getStringFromEnum(e);
}

std::unique_ptr<ISearchContext> ImportedAttributeVector::createSearchContext(std::unique_ptr<QueryTermSimple> term,
                                                                             const SearchContextParams &params) const {
    return std::make_unique<ImportedSearchContext>(std::move(term), params, *this);
}

const IDocumentWeightAttribute *ImportedAttributeVector::asDocumentWeightAttribute() const {
    return nullptr;
}

BasicType::Type ImportedAttributeVector::getBasicType() const {
    return _target_attribute->getBasicType();
}

size_t ImportedAttributeVector::getFixedWidth() const {
    return _target_attribute->getFixedWidth();
}

CollectionType::Type ImportedAttributeVector::getCollectionType() const {
    return _target_attribute->getCollectionType();
}

bool ImportedAttributeVector::hasEnum() const {
    return _target_attribute->hasEnum();
}

void ImportedAttributeVector::clearSearchCache() {
    if (_search_cache) {
        _search_cache->clear();
    }
}

long ImportedAttributeVector::onSerializeForAscendingSort(DocId doc,
                                                          void *serTo,
                                                          long available,
                                                          const common::BlobConverter *bc) const {
    return _target_attribute->serializeForAscendingSort(
            _reference_attribute->getReferencedLid(doc), serTo, available, bc);
}

long ImportedAttributeVector::onSerializeForDescendingSort(DocId doc,
                                                           void *serTo,
                                                           long available,
                                                           const common::BlobConverter *bc) const {
    return _target_attribute->serializeForDescendingSort(
            _reference_attribute->getReferencedLid(doc), serTo, available, bc);
}

}
}
