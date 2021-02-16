// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_read_guard.h"
#include "attributeguard.h"
#include <vespa/vespalib/util/arrayref.h>
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchlib/common/i_document_meta_store_context.h>

namespace search::attribute {

class BitVectorSearchCache;
class ImportedAttributeVector;
class ReferenceAttribute;

/*
 * Short lived attribute vector that does not store values on its own.
 *
 * Read guards are held on
 * - target attribute, to ensure that reads are safe.
 * - target document meta store, to avoid target lids being reused.
 * - reference attribute, to ensure that access to lid mapping is safe.
 *
 * Extra information for direct lid to target lid mapping with
 * boundary check is setup during construction.
 */
class ImportedAttributeVectorReadGuard : public IAttributeVector,
                                         public AttributeReadGuard
{
private:
    using TargetLids = vespalib::ConstArrayRef<uint32_t>;
    IDocumentMetaStoreContext::IReadGuard::UP _target_document_meta_store_read_guard;
    const ImportedAttributeVector   &_imported_attribute;
    TargetLids                       _targetLids;
    AttributeGuard                   _reference_attribute_guard;
    std::unique_ptr<attribute::AttributeReadGuard> _target_attribute_guard;
    const ReferenceAttribute        &_reference_attribute;
protected:
    const IAttributeVector          &_target_attribute;

protected:
    uint32_t getTargetLid(uint32_t lid) const {
        // Check range to avoid reading memory beyond end of mapping array
        return lid < _targetLids.size() ? _targetLids[lid] : 0u;
    }

public:
    ImportedAttributeVectorReadGuard(const ImportedAttributeVector &imported_attribute, bool stableEnumGuard);
    ~ImportedAttributeVectorReadGuard() override;

    const vespalib::string &getName() const override;
    uint32_t getNumDocs() const override;
    uint32_t getValueCount(uint32_t doc) const override;
    uint32_t getMaxValueCount() const override;
    largeint_t getInt(DocId doc) const override;
    double getFloat(DocId doc) const override;
    const char *getString(DocId doc, char *buffer, size_t sz) const override;
    EnumHandle getEnum(DocId doc) const override;
    uint32_t get(DocId docId, largeint_t *buffer, uint32_t sz) const override;
    uint32_t get(DocId docId, double *buffer, uint32_t sz) const override;
    uint32_t get(DocId docId, const char **buffer, uint32_t sz) const override;
    uint32_t get(DocId docId, EnumHandle *buffer, uint32_t sz) const override;
    uint32_t get(DocId docId, WeightedInt *buffer, uint32_t sz) const override;
    uint32_t get(DocId docId, WeightedFloat *buffer, uint32_t sz) const override;
    uint32_t get(DocId docId, WeightedString *buffer, uint32_t sz) const override;
    uint32_t get(DocId docId, WeightedConstChar *buffer, uint32_t sz) const override;
    uint32_t get(DocId docId, WeightedEnum *buffer, uint32_t sz) const override;
    bool findEnum(const char * value, EnumHandle & e) const override;
    std::vector<EnumHandle> findFoldedEnums(const char *value) const override;

    const char * getStringFromEnum(EnumHandle e) const override;
    std::unique_ptr<ISearchContext> createSearchContext(std::unique_ptr<QueryTermSimple> term,
                                                        const SearchContextParams &params) const override;
    const IDocumentWeightAttribute *asDocumentWeightAttribute() const override;
    const tensor::ITensorAttribute *asTensorAttribute() const override;
    BasicType::Type getBasicType() const override;
    size_t getFixedWidth() const override;
    CollectionType::Type getCollectionType() const override;
    bool hasEnum() const override;
    bool getIsFilter() const override;
    bool getIsFastSearch() const override;
    uint32_t getCommittedDocIdLimit() const override;
    bool isImported() const override;
    bool isUndefined(DocId doc) const override;

protected:
    long onSerializeForAscendingSort(DocId doc, void * serTo, long available,
                                     const common::BlobConverter * bc) const override;
    long onSerializeForDescendingSort(DocId doc, void * serTo, long available,
                                      const common::BlobConverter * bc) const override;
};

}
