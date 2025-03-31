// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "floatbase.h"
#include "single_numeric_sort_blob_writer.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/searchcommon/attribute/config.h>

namespace search {

template<typename T>
FloatingPointAttributeTemplate<T>::FloatingPointAttributeTemplate(const std::string & name)
    : FloatingPointAttributeTemplate(name, BasicType::fromType(T()))
{ }

template<typename T>
FloatingPointAttributeTemplate<T>::FloatingPointAttributeTemplate(const std::string & name, const Config & c)
    : FloatingPointAttribute(name, c),
      _defaultValue(ChangeBase::UPDATE, 0, defaultValue())
{
    assert(c.basicType() == BasicType::fromType(T()));
}

template<typename T>
FloatingPointAttributeTemplate<T>::~FloatingPointAttributeTemplate() = default;

template<typename T>
bool
FloatingPointAttributeTemplate<T>::findEnum(const char *value, EnumHandle &e) const {
    vespalib::asciistream iss(value);
    T fvalue = 0;
    try {
        iss >> fvalue;
    } catch (const vespalib::IllegalArgumentException &) {
    }
    return findEnum(fvalue, e);
}

template<typename T>
std::vector<IEnumStore::EnumHandle>
FloatingPointAttributeTemplate<T>::findFoldedEnums(const char *value) const
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
FloatingPointAttributeTemplate<T>::is_sortable() const noexcept
{
    return true;
}

template<typename T>
std::unique_ptr<attribute::ISortBlobWriter>
FloatingPointAttributeTemplate<T>::make_sort_blob_writer(bool ascending, const common::BlobConverter*,
                                                         common::sortspec::MissingPolicy policy,
                                                         std::string_view missing_value) const {
    (void) policy;
    (void) missing_value;
    if (ascending) {
        return std::make_unique<attribute::SingleNumericSortBlobWriter<FloatingPointAttributeTemplate<T>, true>>(*this);
    } else {
        return std::make_unique<attribute::SingleNumericSortBlobWriter<FloatingPointAttributeTemplate<T>, false>>(*this);
    }
}

}
