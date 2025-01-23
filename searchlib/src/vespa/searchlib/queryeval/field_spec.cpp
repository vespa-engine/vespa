// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "field_spec.h"
#include <cassert>

namespace search::queryeval {

FieldSpec::FieldSpec(const std::string & name, uint32_t fieldId, fef::TermFieldHandle handle) noexcept
    : FieldSpec(name, fieldId, handle, false)
{}

FieldSpec::FieldSpec(const std::string & name, uint32_t fieldId,
                     fef::TermFieldHandle handle, bool isFilter_) noexcept
    : FieldSpec(name, fieldId, handle, fef::FilterThreshold(isFilter_))
{}

FieldSpec::FieldSpec(const std::string & name, uint32_t fieldId,
                     fef::TermFieldHandle handle, fef::FilterThreshold threshold) noexcept
    : FieldSpecBase(fieldId, handle, threshold.is_filter()),
      _name(name),
      _threshold(threshold)
{
    // NOTE: Whether the field is a filter is still tracked in FieldSpecBase
    // to ensure this information is available in code where only the base class is used.
    // This also ensures that the size of FieldSpecBase is not changed.
    assert(fieldId < 0x1000000);  // Can be represented by 24 bits
}

FieldSpecBaseList::~FieldSpecBaseList() = default;

FieldSpecList::~FieldSpecList() = default;

FieldSpec::~FieldSpec() = default;

}
