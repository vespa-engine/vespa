// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_data_type.h"
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/vespalib/util/exceptions.h>
#include <sstream>

namespace document {

IMPLEMENT_IDENTIFIABLE_ABSTRACT(TensorDataType, DataType);

TensorDataType::TensorDataType()
    : PrimitiveDataType(DataType::T_TENSOR)
{
}

FieldValue::UP
TensorDataType::createFieldValue() const
{
    return std::make_unique<TensorFieldValue>();
}

TensorDataType*
TensorDataType::clone() const
{
    return new TensorDataType(*this);
}

void
TensorDataType::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "TensorDataType()";
}

std::unique_ptr<const TensorDataType>
TensorDataType::fromSpec([[maybe_unused]] const vespalib::string &spec)
{
    return std::make_unique<const TensorDataType>();
}

} // document
