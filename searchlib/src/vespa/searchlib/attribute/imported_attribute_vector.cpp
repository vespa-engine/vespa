// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "imported_attribute_vector.h"
#include "attributeguard.h"
#include <vespa/vespalib/util/exceptions.h>

namespace search {
namespace attribute {

ImportedAttributeVector::ImportedAttributeVector(
            vespalib::stringref name,
            std::shared_ptr<ReferenceAttribute> reference_attribute,
            std::shared_ptr<AttributeVector> target_attribute)
    : _name(name),
      _reference_attribute(std::move(reference_attribute)),
      _target_attribute(std::move(target_attribute))
{
}

ImportedAttributeVector::~ImportedAttributeVector() {
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

std::unique_ptr<ISearchContext> ImportedAttributeVector::createSearchContext(std::unique_ptr<QueryTermSimple> term,
                                                                             const SearchContextParams &params) const {
    (void) term;
    (void) params;
    return std::unique_ptr<ISearchContext>();
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

long ImportedAttributeVector::onSerializeForAscendingSort(DocId doc,
                                                          void *serTo,
                                                          long available,
                                                          const common::BlobConverter *bc) const {
    return _target_attribute->serializeForAscendingSort(doc, serTo, available, bc);
}

long ImportedAttributeVector::onSerializeForDescendingSort(DocId doc,
                                                           void *serTo,
                                                           long available,
                                                           const common::BlobConverter *bc) const {
    return _target_attribute->serializeForDescendingSort(doc, serTo, available, bc);
}

namespace {

class ImportedAttributeGuard : public AttributeGuard {
public:
    ImportedAttributeGuard(const AttributeVectorSP& target_attr,
                           const AttributeVectorSP& reference_attr)
        : AttributeGuard(),
          _target_attr_guard(target_attr),
          _reference_attr_guard(reference_attr)
    {
    }
    
private:
    AttributeGuard _target_attr_guard;
    AttributeGuard _reference_attr_guard;
};

class ImportedAttributeEnumGuard : public AttributeEnumGuard {
public:
    ImportedAttributeEnumGuard(const AttributeVectorSP& target_attr,
                               const AttributeVectorSP& reference_attr)
        : AttributeEnumGuard(AttributeVectorSP()),
          _target_attr_enum_guard(target_attr),
          _reference_attr_guard(reference_attr)
    {
    }
    
private:
    AttributeEnumGuard _target_attr_enum_guard;
    AttributeGuard _reference_attr_guard;
};

}

std::unique_ptr<AttributeGuard> ImportedAttributeVector::acquireGuard() const {
    return std::make_unique<ImportedAttributeGuard>(_target_attribute, _reference_attribute);
}

std::unique_ptr<AttributeEnumGuard> ImportedAttributeVector::acquireEnumGuard() const {
    return std::make_unique<ImportedAttributeEnumGuard>(_target_attribute, _reference_attribute);
}

}
}
