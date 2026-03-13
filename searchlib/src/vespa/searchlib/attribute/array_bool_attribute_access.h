// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributevector.h"
#include <vespa/searchcommon/attribute/i_multi_value_attribute.h>

namespace search::attribute {

/**
 * Base class for array bool attributes providing accessor methods
 * implemented in terms of a virtual get_bools() function.
 *
 * Subclasses implement get_bools() with their own storage strategy:
 * - ArrayBoolAttribute: indexed search (RcuVector + RawBufferStore)
 * - ArrayBoolExtAttribute: streaming search (flat vectors, IExtendAttribute)
 */
class ArrayBoolAttributeAccess : public AttributeVector,
                                 public IMultiValueAttribute
{
protected:
    ArrayBoolAttributeAccess(const std::string& name, const Config& config);
    ~ArrayBoolAttributeAccess() override;

public:
    virtual vespalib::BitSpan get_bools(DocId docid) const = 0;

    // Value access (implemented via get_bools)
    uint32_t getValueCount(DocId doc) const override;
    largeint_t getInt(DocId doc) const override;
    double getFloat(DocId doc) const override;
    std::span<const char> get_raw(DocId doc) const override;
    uint32_t get(DocId doc, largeint_t* v, uint32_t sz) const override;
    uint32_t get(DocId doc, double* v, uint32_t sz) const override;
    uint32_t get(DocId doc, std::string* v, uint32_t sz) const override;
    uint32_t get(DocId doc, const char** v, uint32_t sz) const override;
    uint32_t get(DocId doc, EnumHandle* e, uint32_t sz) const override;
    uint32_t get(DocId doc, WeightedInt* v, uint32_t sz) const override;
    uint32_t get(DocId doc, WeightedFloat* v, uint32_t sz) const override;
    uint32_t get(DocId doc, WeightedString* v, uint32_t sz) const override;
    uint32_t get(DocId doc, WeightedConstChar* v, uint32_t sz) const override;
    uint32_t get(DocId doc, WeightedEnum* v, uint32_t sz) const override;
    uint32_t getEnum(DocId doc) const override;
    bool is_sortable() const noexcept override;
    std::unique_ptr<attribute::ISortBlobWriter> make_sort_blob_writer(bool ascending, const common::BlobConverter* converter,
                                                                      common::sortspec::MissingPolicy policy,
                                                                      std::string_view missing_value) const override;

    // IMultiValueAttribute
    const IMultiValueAttribute* as_multi_value_attribute() const override;
};

}
