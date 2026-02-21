// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "array_bool_attribute_access.h"
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcommon/attribute/i_sort_blob_writer.h>
#include <cassert>

namespace search::attribute {

using largeint_t = IAttributeVector::largeint_t;

ArrayBoolAttributeAccess::ArrayBoolAttributeAccess(const std::string& name, const Config& config)
    : AttributeVector(name, config)
{
}

ArrayBoolAttributeAccess::~ArrayBoolAttributeAccess() = default;

uint32_t
ArrayBoolAttributeAccess::getValueCount(DocId doc) const
{
    return get_bools(doc).size();
}

largeint_t
ArrayBoolAttributeAccess::getInt(DocId doc) const
{
    auto bools = get_bools(doc);
    return (bools.size() > 0 && bools[0]) ? 1 : 0;
}

double
ArrayBoolAttributeAccess::getFloat(DocId doc) const
{
    return static_cast<double>(getInt(doc));
}

std::span<const char>
ArrayBoolAttributeAccess::get_raw(DocId) const
{
    return {};
}

uint32_t
ArrayBoolAttributeAccess::get(DocId doc, largeint_t* v, uint32_t sz) const
{
    auto bools = get_bools(doc);
    uint32_t n = std::min(bools.size(), sz);
    for (uint32_t i = 0; i < n; ++i) {
        v[i] = bools[i] ? 1 : 0;
    }
    return bools.size();
}

uint32_t
ArrayBoolAttributeAccess::get(DocId doc, double* v, uint32_t sz) const
{
    auto bools = get_bools(doc);
    uint32_t n = std::min(bools.size(), sz);
    for (uint32_t i = 0; i < n; ++i) {
        v[i] = bools[i] ? 1.0 : 0.0;
    }
    return bools.size();
}

uint32_t
ArrayBoolAttributeAccess::get(DocId doc, std::string* v, uint32_t sz) const
{
    auto bools = get_bools(doc);
    uint32_t n = std::min(bools.size(), sz);
    for (uint32_t i = 0; i < n; ++i) {
        v[i] = bools[i] ? "1" : "0";
    }
    return bools.size();
}

uint32_t
ArrayBoolAttributeAccess::get(DocId, const char**, uint32_t) const
{
    return 0;
}

uint32_t
ArrayBoolAttributeAccess::get(DocId, EnumHandle*, uint32_t) const
{
    return 0;
}

uint32_t
ArrayBoolAttributeAccess::get(DocId doc, WeightedInt* v, uint32_t sz) const
{
    auto bools = get_bools(doc);
    uint32_t n = std::min(bools.size(), sz);
    for (uint32_t i = 0; i < n; ++i) {
        v[i] = WeightedInt(bools[i] ? 1 : 0);
    }
    return bools.size();
}

uint32_t
ArrayBoolAttributeAccess::get(DocId doc, WeightedFloat* v, uint32_t sz) const
{
    auto bools = get_bools(doc);
    uint32_t n = std::min(bools.size(), sz);
    for (uint32_t i = 0; i < n; ++i) {
        v[i] = WeightedFloat(bools[i] ? 1.0 : 0.0);
    }
    return bools.size();
}

uint32_t
ArrayBoolAttributeAccess::get(DocId doc, WeightedString* v, uint32_t sz) const
{
    auto bools = get_bools(doc);
    uint32_t n = std::min(bools.size(), sz);
    for (uint32_t i = 0; i < n; ++i) {
        v[i] = WeightedString(bools[i] ? "1" : "0");
    }
    return bools.size();
}

uint32_t
ArrayBoolAttributeAccess::get(DocId, WeightedConstChar*, uint32_t) const
{
    return 0;
}

uint32_t
ArrayBoolAttributeAccess::get(DocId, WeightedEnum*, uint32_t) const
{
    return 0;
}

uint32_t
ArrayBoolAttributeAccess::getEnum(DocId) const
{
    return std::numeric_limits<uint32_t>::max();
}

bool
ArrayBoolAttributeAccess::is_sortable() const noexcept
{
    return false;
}

std::unique_ptr<attribute::ISortBlobWriter>
ArrayBoolAttributeAccess::make_sort_blob_writer(bool, const common::BlobConverter*,
                                                common::sortspec::MissingPolicy,
                                                std::string_view) const
{
    assert(false && "ArrayBoolAttributeAccess is not sortable");
    return {};
}

const IMultiValueAttribute*
ArrayBoolAttributeAccess::as_multi_value_attribute() const
{
    return this;
}

}
