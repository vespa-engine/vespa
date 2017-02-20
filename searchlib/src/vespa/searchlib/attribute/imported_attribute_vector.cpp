// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "imported_attribute_vector.h"
#include <vespa/vespalib/util/exceptions.h>

namespace search {
namespace attribute {

ImportedAttributeVector::ImportedAttributeVector(
            vespalib::stringref name,
            std::shared_ptr<ReferenceAttribute> reference_attribute,
            std::shared_ptr<IAttributeVector> target_vector)
    : _name(name),
      _reference_attribute(std::move(reference_attribute)),
      _target_vector(std::move(target_vector))
{
}

ImportedAttributeVector::~ImportedAttributeVector() {
}

namespace {
[[noreturn]] void not_implemented() {
    throw vespalib::IllegalStateException("not implemented");
}
}

const vespalib::string& search::attribute::ImportedAttributeVector::getName() const {
    return _name;
}

uint32_t ImportedAttributeVector::getNumDocs() const {
    return _reference_attribute->getNumDocs();
}

uint32_t ImportedAttributeVector::getValueCount(uint32_t doc) const {
    (void) doc;
    return 0; // TODO
}

uint32_t ImportedAttributeVector::getMaxValueCount() const {
    return 0; // TODO
}

IAttributeVector::largeint_t ImportedAttributeVector::getInt(DocId doc) const {
    return _target_vector->getInt(_reference_attribute->getReferencedLid(doc));
}

double ImportedAttributeVector::getFloat(DocId doc) const {
    return _target_vector->getFloat(_reference_attribute->getReferencedLid(doc));
}

const char *ImportedAttributeVector::getString(DocId doc, char *buffer, size_t sz) const {
    return _target_vector->getString(_reference_attribute->getReferencedLid(doc), buffer, sz);
}

IAttributeVector::EnumHandle ImportedAttributeVector::getEnum(DocId doc) const {
    (void) doc;
    return 0; // TODO
}

uint32_t ImportedAttributeVector::get(DocId docId, largeint_t *buffer, uint32_t sz) const {
    return _target_vector->get(_reference_attribute->getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVector::get(DocId docId, double *buffer, uint32_t sz) const {
    return _target_vector->get(_reference_attribute->getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVector::get(DocId docId, const char **buffer, uint32_t sz) const {
    return _target_vector->get(_reference_attribute->getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVector::get(DocId docId, EnumHandle *buffer, uint32_t sz) const {
    (void) docId;
    (void) buffer;
    (void) sz;
    return 0; // TODO
}

uint32_t ImportedAttributeVector::get(DocId docId, WeightedInt *buffer, uint32_t sz) const {
    return _target_vector->get(_reference_attribute->getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVector::get(DocId docId, WeightedFloat *buffer, uint32_t sz) const {
    return _target_vector->get(_reference_attribute->getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVector::get(DocId docId, WeightedString *buffer, uint32_t sz) const {
    (void) docId;
    (void) buffer;
    (void) sz;
    return 0; // TODO
}

uint32_t ImportedAttributeVector::get(DocId docId, WeightedConstChar *buffer, uint32_t sz) const {
    (void) docId;
    (void) buffer;
    (void) sz;
    return 0; // TODO
}

uint32_t ImportedAttributeVector::get(DocId docId, WeightedEnum *buffer, uint32_t sz) const {
    (void) docId;
    (void) buffer;
    (void) sz;
    return 0; // TODO
}

bool ImportedAttributeVector::findEnum(const char *value, EnumHandle &e) const {
    (void) value;
    (void) e;
    return false; // TODO
}

BasicType::Type ImportedAttributeVector::getBasicType() const {
    return _target_vector->getBasicType();
}

size_t ImportedAttributeVector::getFixedWidth() const {
    return 0; // TODO
}

CollectionType::Type ImportedAttributeVector::getCollectionType() const {
    return _target_vector->getCollectionType();
}

bool ImportedAttributeVector::hasEnum() const {
    return false;
}

long ImportedAttributeVector::onSerializeForAscendingSort(DocId doc,
                                                          void *serTo,
                                                          long available,
                                                          const common::BlobConverter *bc) const {
    (void) doc;
    (void) serTo;
    (void) available;
    (void) bc;
    not_implemented(); // TODO forward to target
}

long ImportedAttributeVector::onSerializeForDescendingSort(DocId doc,
                                                           void *serTo,
                                                           long available,
                                                           const common::BlobConverter *bc) const {
    (void) doc;
    (void) serTo;
    (void) available;
    (void) bc;
    not_implemented(); // TODO forward to target
}

}
}
