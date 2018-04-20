// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "imported_attribute_vector_read_guard.h"
#include "imported_attribute_vector.h"
#include "imported_search_context.h"
#include "reference_attribute.h"
#include <vespa/searchlib/common/i_gid_to_lid_mapper.h>
#include <vespa/searchlib/common/i_gid_to_lid_mapper_factory.h>
#include <vespa/searchlib/query/queryterm.h>

namespace search {
namespace attribute {

ImportedAttributeVectorReadGuard::ImportedAttributeVectorReadGuard(
        const ImportedAttributeVector &imported_attribute,
        bool stableEnumGuard)
    : AttributeReadGuard(this),
      _imported_attribute(imported_attribute),
      _referencedLids(),
      _reference_attribute_guard(imported_attribute.getReferenceAttribute()),
      _target_attribute_guard(imported_attribute.getTargetAttribute()->makeReadGuard(stableEnumGuard)),
      _reference_attribute(*imported_attribute.getReferenceAttribute()),
      _target_attribute(*_target_attribute_guard->attribute()),
      _mapper(_reference_attribute.getGidToLidMapperFactory()->getMapper())
{
    _referencedLids = _reference_attribute.getReferencedLids();
}

ImportedAttributeVectorReadGuard::~ImportedAttributeVectorReadGuard() {
}

const vespalib::string& ImportedAttributeVectorReadGuard::getName() const {
    return _imported_attribute.getName();
}

uint32_t ImportedAttributeVectorReadGuard::getNumDocs() const {
    return _reference_attribute.getNumDocs();
}

uint32_t ImportedAttributeVectorReadGuard::getValueCount(uint32_t doc) const {
    return _target_attribute.getValueCount(getReferencedLid(doc));
}

uint32_t ImportedAttributeVectorReadGuard::getMaxValueCount() const {
    return _target_attribute.getMaxValueCount();
}

IAttributeVector::largeint_t ImportedAttributeVectorReadGuard::getInt(DocId doc) const {
    return _target_attribute.getInt(getReferencedLid(doc));
}

double ImportedAttributeVectorReadGuard::getFloat(DocId doc) const {
    return _target_attribute.getFloat(getReferencedLid(doc));
}

const char *ImportedAttributeVectorReadGuard::getString(DocId doc, char *buffer, size_t sz) const {
    return _target_attribute.getString(getReferencedLid(doc), buffer, sz);
}

IAttributeVector::EnumHandle ImportedAttributeVectorReadGuard::getEnum(DocId doc) const {
    return _target_attribute.getEnum(getReferencedLid(doc));
}

uint32_t ImportedAttributeVectorReadGuard::get(DocId docId, largeint_t *buffer, uint32_t sz) const {
    return _target_attribute.get(getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVectorReadGuard::get(DocId docId, double *buffer, uint32_t sz) const {
    return _target_attribute.get(getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVectorReadGuard::get(DocId docId, const char **buffer, uint32_t sz) const {
    return _target_attribute.get(getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVectorReadGuard::get(DocId docId, EnumHandle *buffer, uint32_t sz) const {
    return _target_attribute.get(getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVectorReadGuard::get(DocId docId, WeightedInt *buffer, uint32_t sz) const {
    return _target_attribute.get(getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVectorReadGuard::get(DocId docId, WeightedFloat *buffer, uint32_t sz) const {
    return _target_attribute.get(getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVectorReadGuard::get(DocId docId, WeightedString *buffer, uint32_t sz) const {
    return _target_attribute.get(getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVectorReadGuard::get(DocId docId, WeightedConstChar *buffer, uint32_t sz) const {
    return _target_attribute.get(getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVectorReadGuard::get(DocId docId, WeightedEnum *buffer, uint32_t sz) const {
    return _target_attribute.get(getReferencedLid(docId), buffer, sz);
}

bool ImportedAttributeVectorReadGuard::findEnum(const char *value, EnumHandle &e) const {
    return _target_attribute.findEnum(value, e);
}

const char * ImportedAttributeVectorReadGuard::getStringFromEnum(EnumHandle e) const {
    return _target_attribute.getStringFromEnum(e);
}

std::unique_ptr<ISearchContext> ImportedAttributeVectorReadGuard::createSearchContext(std::unique_ptr<QueryTermSimple> term,
                                                                             const SearchContextParams &params) const {
    return std::make_unique<ImportedSearchContext>(std::move(term), params, _imported_attribute, _target_attribute);
}

const IDocumentWeightAttribute *ImportedAttributeVectorReadGuard::asDocumentWeightAttribute() const {
    return nullptr;
}

const tensor::ITensorAttribute *ImportedAttributeVectorReadGuard::asTensorAttribute() const {
    return nullptr;
}

BasicType::Type ImportedAttributeVectorReadGuard::getBasicType() const {
    return _target_attribute.getBasicType();
}

size_t ImportedAttributeVectorReadGuard::getFixedWidth() const {
    return _target_attribute.getFixedWidth();
}

CollectionType::Type ImportedAttributeVectorReadGuard::getCollectionType() const {
    return _target_attribute.getCollectionType();
}

bool ImportedAttributeVectorReadGuard::hasEnum() const {
    return _target_attribute.hasEnum();
}

bool ImportedAttributeVectorReadGuard::getIsFilter() const {
    return _target_attribute.getIsFilter();
}

bool ImportedAttributeVectorReadGuard::getIsFastSearch() const {
    return _target_attribute.getIsFastSearch();
}

uint32_t ImportedAttributeVectorReadGuard::getCommittedDocIdLimitSlow() const {
    return _reference_attribute.getCommittedDocIdLimit();
}

bool ImportedAttributeVectorReadGuard::isImported() const
{
    return true;
}

long ImportedAttributeVectorReadGuard::onSerializeForAscendingSort(DocId doc,
                                                                   void *serTo,
                                                                   long available,
                                                                   const common::BlobConverter *bc) const {
    return _target_attribute.serializeForAscendingSort(getReferencedLid(doc), serTo, available, bc);
}

long ImportedAttributeVectorReadGuard::onSerializeForDescendingSort(DocId doc,
                                                                    void *serTo,
                                                                    long available,
                                                                    const common::BlobConverter *bc) const {
    return _target_attribute.serializeForDescendingSort(getReferencedLid(doc), serTo, available, bc);
}

}
}
