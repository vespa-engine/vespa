// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "numericdatatype.h"
#include <ostream>

namespace document {

NumericDataType::NumericDataType(Type type)
    : PrimitiveDataType(type)
{
}

void NumericDataType::print(std::ostream& out, bool, const std::string&) const
{
    out << "NumericDataType(" << getName() << ", id " << getId() << ")";
}
}  // namespace document
