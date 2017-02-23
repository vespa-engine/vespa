// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "reference_attribute.h"
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace search {
namespace attribute {

/**
 * TODO document
 */
class ImportedAttributeVector : public IAttributeVector {
public:
    ImportedAttributeVector(vespalib::stringref name,
                            std::shared_ptr<ReferenceAttribute> reference_attribute,
                            std::shared_ptr<IAttributeVector> target_vector);
    ~ImportedAttributeVector();

    const vespalib::string & getName() const override;
    uint32_t getNumDocs() const override;
    uint32_t getValueCount(uint32_t doc) const override;
    uint32_t getMaxValueCount() const override;
    largeint_t getInt(DocId doc) const override;
    double getFloat(DocId doc) const override;
    const char * getString(DocId doc, char * buffer, size_t sz) const override;
    EnumHandle getEnum(DocId doc) const override;
    uint32_t get(DocId docId, largeint_t * buffer, uint32_t sz) const override;
    uint32_t get(DocId docId, double * buffer, uint32_t sz) const override;
    uint32_t get(DocId docId, const char ** buffer, uint32_t sz) const override;
    uint32_t get(DocId docId, EnumHandle * buffer, uint32_t sz) const override;
    uint32_t get(DocId docId, WeightedInt * buffer, uint32_t sz) const override;
    uint32_t get(DocId docId, WeightedFloat * buffer, uint32_t sz) const override;
    uint32_t get(DocId docId, WeightedString * buffer, uint32_t sz) const override;
    uint32_t get(DocId docId, WeightedConstChar * buffer, uint32_t sz) const override;
    uint32_t get(DocId docId, WeightedEnum * buffer, uint32_t sz) const override;
    bool findEnum(const char * value, EnumHandle & e) const override;
    BasicType::Type getBasicType() const override;
    size_t getFixedWidth() const override;
    CollectionType::Type getCollectionType() const override;
    bool hasEnum() const override;

private:
    long onSerializeForAscendingSort(DocId doc, void * serTo, long available,
                                     const common::BlobConverter * bc) const override;
    long onSerializeForDescendingSort(DocId doc, void * serTo, long available,
                                      const common::BlobConverter * bc) const override;


    vespalib::string                    _name;
    std::shared_ptr<ReferenceAttribute> _reference_attribute;
    std::shared_ptr<IAttributeVector>   _target_vector;
};

} // attribute
} // search