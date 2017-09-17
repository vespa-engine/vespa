// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "imported_attribute_vector_read_guard.h"
#include <vespa/searchlib/common/i_gid_to_lid_mapper.h>
#include <vespa/searchlib/common/i_gid_to_lid_mapper_factory.h>

namespace search {
namespace attribute {

ImportedAttributeVectorReadGuard::ImportedAttributeVectorReadGuard(
        vespalib::stringref name,
        std::shared_ptr<ReferenceAttribute> reference_attribute,
        std::shared_ptr<AttributeVector> target_attribute,
        std::shared_ptr<IDocumentMetaStoreContext> document_meta_store,
        std::shared_ptr<BitVectorSearchCache> search_cache,
        bool stableEnumGuard)
    : ImportedAttributeVector(name, std::move(reference_attribute), std::move(target_attribute),
                              std::move(document_meta_store), std::move(search_cache)),
      _referencedLids(),
      _reference_attribute_guard(_reference_attribute),
      _target_attribute_guard(stableEnumGuard ? std::shared_ptr<AttributeVector>() : _target_attribute),
      _target_attribute_enum_guard(stableEnumGuard ? _target_attribute : std::shared_ptr<AttributeVector>()),
      _mapper(_reference_attribute->getGidToLidMapperFactory()->getMapper())
{
    _referencedLids = _reference_attribute->getReferencedLids();
}

ImportedAttributeVectorReadGuard::~ImportedAttributeVectorReadGuard() {
}

uint32_t ImportedAttributeVectorReadGuard::getValueCount(uint32_t doc) const {
    return _target_attribute->getValueCount(getReferencedLid(doc));
}

IAttributeVector::largeint_t ImportedAttributeVectorReadGuard::getInt(DocId doc) const {
    return _target_attribute->getInt(getReferencedLid(doc));
}

double ImportedAttributeVectorReadGuard::getFloat(DocId doc) const {
    return _target_attribute->getFloat(getReferencedLid(doc));
}

const char *ImportedAttributeVectorReadGuard::getString(DocId doc, char *buffer, size_t sz) const {
    return _target_attribute->getString(getReferencedLid(doc), buffer, sz);
}

IAttributeVector::EnumHandle ImportedAttributeVectorReadGuard::getEnum(DocId doc) const {
    return _target_attribute->getEnum(getReferencedLid(doc));
}

uint32_t ImportedAttributeVectorReadGuard::get(DocId docId, largeint_t *buffer, uint32_t sz) const {
    return _target_attribute->get(getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVectorReadGuard::get(DocId docId, double *buffer, uint32_t sz) const {
    return _target_attribute->get(getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVectorReadGuard::get(DocId docId, const char **buffer, uint32_t sz) const {
    return _target_attribute->get(getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVectorReadGuard::get(DocId docId, EnumHandle *buffer, uint32_t sz) const {
    return _target_attribute->get(getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVectorReadGuard::get(DocId docId, WeightedInt *buffer, uint32_t sz) const {
    return _target_attribute->get(getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVectorReadGuard::get(DocId docId, WeightedFloat *buffer, uint32_t sz) const {
    return _target_attribute->get(getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVectorReadGuard::get(DocId docId, WeightedString *buffer, uint32_t sz) const {
    return _target_attribute->get(getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVectorReadGuard::get(DocId docId, WeightedConstChar *buffer, uint32_t sz) const {
    return _target_attribute->get(getReferencedLid(docId), buffer, sz);
}

uint32_t ImportedAttributeVectorReadGuard::get(DocId docId, WeightedEnum *buffer, uint32_t sz) const {
    return _target_attribute->get(getReferencedLid(docId), buffer, sz);
}

long ImportedAttributeVectorReadGuard::onSerializeForAscendingSort(DocId doc,
                                                                   void *serTo,
                                                                   long available,
                                                                   const common::BlobConverter *bc) const {
    return _target_attribute->serializeForAscendingSort(getReferencedLid(doc), serTo, available, bc);
}

long ImportedAttributeVectorReadGuard::onSerializeForDescendingSort(DocId doc,
                                                                    void *serTo,
                                                                    long available,
                                                                    const common::BlobConverter *bc) const {
    return _target_attribute->serializeForDescendingSort(getReferencedLid(doc), serTo, available, bc);
}

}
}
