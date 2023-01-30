// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "field_spec.h"
#include <cassert>

namespace search::queryeval {

FieldSpecBase::FieldSpecBase(uint32_t fieldId, fef::TermFieldHandle handle, bool isFilter_) :
    _fieldId(fieldId | (isFilter_ ? 0x1000000u : 0)),
    _handle(handle)
{
    assert(fieldId < 0x1000000);  // Can be represented by 24 bits
}

FieldSpecBaseList::~FieldSpecBaseList() = default;

FieldSpecList::~FieldSpecList() = default;

FieldSpec::~FieldSpec() = default;

}
