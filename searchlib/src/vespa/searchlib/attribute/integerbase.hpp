// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "integerbase.h"
#include "single_numeric_sort_blob_writer.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/searchcommon/attribute/config.h>

namespace search {

using largeint_t = attribute::IAttributeVector::largeint_t;

template<typename T>
IntegerAttributeTemplate<T>::IntegerAttributeTemplate(const std::string & name)
    : IntegerAttributeTemplate(name, BasicType::fromType(T()))
{ }

template<typename T>
IntegerAttributeTemplate<T>::IntegerAttributeTemplate(const std::string & name, const Config & c)
    : IntegerAttribute(name, c),
      _defaultValue(ChangeBase::UPDATE, 0, defaultValue())
{
    assert(c.basicType() == BasicType::fromType(T()));
}

template<typename T>
IntegerAttributeTemplate<T>::IntegerAttributeTemplate(const std::string & name, const Config & c, const BasicType &realType)
    : IntegerAttribute(name, c),
      _defaultValue(ChangeBase::UPDATE, 0, 0u)
{
    assert(c.basicType() == realType);
    (void) realType;
    assert(BasicType::fromType(T()) == BasicType::INT8);
}

template<typename T>
IntegerAttributeTemplate<T>::~IntegerAttributeTemplate() = default;

template<typename T>
bool
IntegerAttributeTemplate<T>::findEnum(const char *value, EnumHandle &e) const {
    vespalib::asciistream iss(value);
    int64_t ivalue = 0;
    try {
        iss >> ivalue;
    } catch (const vespalib::IllegalArgumentException &) {
    }
    return findEnum(ivalue, e);
}


template<typename T>
std::vector<IEnumStore::EnumHandle>
IntegerAttributeTemplate<T>::findFoldedEnums(const char *value) const
{
    std::vector<EnumHandle> result;
    EnumHandle h;
    if (findEnum(value, h)) {
        result.push_back(h);
    }
    return result;
}

template<typename T>
bool
IntegerAttributeTemplate<T>::is_sortable() const noexcept
{
    return true;
}

template<typename T>
std::unique_ptr<attribute::ISortBlobWriter>
IntegerAttributeTemplate<T>::make_sort_blob_writer(bool ascending, const common::BlobConverter*,
                                                   common::sortspec::MissingPolicy policy,
                                                   std::string_view missing_value) const
{
    return make_single_numeric_sort_blob_writer<IntegerAttributeTemplate<T>>(*this, ascending, policy, missing_value);
}

}

