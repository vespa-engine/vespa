// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_read_guard.h"
#include "attributeguard.h"
#include <vespa/vespalib/util/arrayref.h>
#include <vespa/searchcommon/attribute/iattributevector.h>

namespace search { class IGidToLidMapper; }

namespace search::attribute {

class BitVectorSearchCache;
class ImportedAttributeVector;
class ReferenceAttribute;

/*
 * Short lived attribute vector that does not store values on its own.
 *
 * Extra information for direct lid to referenced lid mapping with
 * boundary check is setup during construction.
 */
class ImportedAttributeVectorReadGuard : public IAttributeVector,
                                         public AttributeReadGuard
{
private:
    using ReferencedLids = vespalib::ConstArrayRef<uint32_t>;
    const ImportedAttributeVector   &_imported_attribute;
    ReferencedLids                   _referencedLids;
    AttributeGuard                   _reference_attribute_guard;
    std::unique_ptr<attribute::AttributeReadGuard> _target_attribute_guard;
    const ReferenceAttribute        &_reference_attribute;
protected:
    const IAttributeVector          &_target_attribute;
private:
    std::unique_ptr<IGidToLidMapper> _mapper;

protected:
    uint32_t getReferencedLid(uint32_t lid) const {
        return _referencedLids[lid];
    }

public:
    ImportedAttributeVectorReadGuard(const ImportedAttributeVector &imported_attribute,
                                     bool stableEnumGuard);
    ~ImportedAttributeVectorReadGuard();

    virtual const vespalib::string &getName() const override;
    virtual uint32_t getNumDocs() const override;
    virtual uint32_t getValueCount(uint32_t doc) const override;
    virtual uint32_t getMaxValueCount() const override;
    virtual largeint_t getInt(DocId doc) const override;
    virtual double getFloat(DocId doc) const override;
    virtual const char *getString(DocId doc, char *buffer, size_t sz) const override;
    virtual EnumHandle getEnum(DocId doc) const override;
    virtual uint32_t get(DocId docId, largeint_t *buffer, uint32_t sz) const override;
    virtual uint32_t get(DocId docId, double *buffer, uint32_t sz) const override;
    virtual uint32_t get(DocId docId, const char **buffer, uint32_t sz) const override;
    virtual uint32_t get(DocId docId, EnumHandle *buffer, uint32_t sz) const override;
    virtual uint32_t get(DocId docId, WeightedInt *buffer, uint32_t sz) const override;
    virtual uint32_t get(DocId docId, WeightedFloat *buffer, uint32_t sz) const override;
    virtual uint32_t get(DocId docId, WeightedString *buffer, uint32_t sz) const override;
    virtual uint32_t get(DocId docId, WeightedConstChar *buffer, uint32_t sz) const override;
    virtual uint32_t get(DocId docId, WeightedEnum *buffer, uint32_t sz) const override;
    virtual bool findEnum(const char * value, EnumHandle & e) const override;
    virtual const char * getStringFromEnum(EnumHandle e) const override;
    virtual std::unique_ptr<ISearchContext> createSearchContext(std::unique_ptr<QueryTermSimple> term,
                                                                const SearchContextParams &params) const override;
    virtual const IDocumentWeightAttribute *asDocumentWeightAttribute() const override;
    virtual const tensor::ITensorAttribute *asTensorAttribute() const override;
    virtual BasicType::Type getBasicType() const override;
    virtual size_t getFixedWidth() const override;
    virtual CollectionType::Type getCollectionType() const override;
    virtual bool hasEnum() const override;
    virtual bool getIsFilter() const override;
    virtual bool getIsFastSearch() const override;
    virtual uint32_t getCommittedDocIdLimitSlow() const override;
    virtual bool isImported() const override;

protected:
    virtual long onSerializeForAscendingSort(DocId doc, void * serTo, long available,
                                             const common::BlobConverter * bc) const override;
    virtual long onSerializeForDescendingSort(DocId doc, void * serTo, long available,
                                              const common::BlobConverter * bc) const override;
};

}
