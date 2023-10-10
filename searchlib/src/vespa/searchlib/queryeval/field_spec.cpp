// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "field_spec.h"
#include <cassert>

namespace search::queryeval {

FieldSpec::FieldSpec(const vespalib::string & name, uint32_t fieldId, fef::TermFieldHandle handle) noexcept
    : FieldSpec(name, fieldId, handle, false)
{}

FieldSpec::FieldSpec(const vespalib::string & name, uint32_t fieldId,
                     fef::TermFieldHandle handle, bool isFilter_) noexcept
    : FieldSpecBase(fieldId, handle, isFilter_),
      _name(name)
{
    assert(fieldId < 0x1000000);  // Can be represented by 24 bits
}

FieldSpecBaseList::~FieldSpecBaseList() = default;

FieldSpecList::~FieldSpecList() = default;

FieldSpec::~FieldSpec() = default;

}
