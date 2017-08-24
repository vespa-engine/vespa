// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "imported_attribute_vector.h"
#include "attributeguard.h"

namespace search::attribute {

/*
 * Short lived attribute vector that does not store values on its own.
 *
 * Extra information for direct lid to referenced lid mapping with
 * boundary check is setup during construction.
 */
class ImportedAttributeVectorReadGuard : public ImportedAttributeVector
{
    using ReferencedLids = vespalib::ConstArrayRef<uint32_t>;
    ReferencedLids                      _referencedLids;
    uint32_t                            _referencedLidLimit;
    AttributeGuard                      _reference_attribute_guard;
    AttributeGuard                      _target_attribute_guard;
    AttributeEnumGuard                  _target_attribute_enum_guard;

    uint32_t getReferencedLid(uint32_t lid) const {
        uint32_t referencedLid = _referencedLids[lid];
        return ((referencedLid >= _referencedLidLimit) ? 0u : referencedLid);
    }

public:
    ImportedAttributeVectorReadGuard(vespalib::stringref name,
                                     std::shared_ptr<ReferenceAttribute> reference_attribute,
                                     std::shared_ptr<AttributeVector> target_attribute,
                                     bool stableEnumGuard);
    ~ImportedAttributeVectorReadGuard();

    virtual uint32_t getValueCount(uint32_t doc) const override;
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
protected:
    virtual long onSerializeForAscendingSort(DocId doc, void * serTo, long available,
                                             const common::BlobConverter * bc) const override;
    virtual long onSerializeForDescendingSort(DocId doc, void * serTo, long available,
                                              const common::BlobConverter * bc) const override;
};

}
