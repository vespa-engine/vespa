// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "literalfieldvalue.h"
#include <vespa/vespalib/util/exceptions.h>

namespace document {

template<typename SubClass, int type, bool addZeroTerm>
const DataType *
LiteralFieldValue<SubClass, type, addZeroTerm>::getDataType() const
{
    switch (type) {
    case DataType::T_URI:    return DataType::URI;
    case DataType::T_STRING: return DataType::STRING;
    case DataType::T_RAW:    return DataType::RAW;
    default:
        throw vespalib::IllegalStateException(vespalib::make_string(
                        "Illegal literal type id %i", type), VESPA_STRLOC);
    }
}

}
