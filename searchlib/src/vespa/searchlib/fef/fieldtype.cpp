// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "fieldtype.h"

namespace search {
namespace fef {

FieldType::FieldType(uint32_t value)
    : _value(value)
{
}

const FieldType FieldType::INDEX(1);

const FieldType FieldType::ATTRIBUTE(2);

const FieldType FieldType::HIDDEN_ATTRIBUTE(3);

} // namespace fef
} // namespace search
