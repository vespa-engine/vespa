// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_data_type.h"
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/vespalib/util/exceptions.h>
#include <sstream>

using vespalib::eval::ValueType;

namespace document {

IMPLEMENT_IDENTIFIABLE_ABSTRACT(TensorDataType, DataType);

TensorDataType::TensorDataType()
    : TensorDataType(ValueType::error_type())
{
}

TensorDataType::TensorDataType(ValueType tensorType)
    : PrimitiveDataType(DataType::T_TENSOR),
      _tensorType(std::move(tensorType))
{
}

FieldValue::UP
TensorDataType::createFieldValue() const
{
    return std::make_unique<TensorFieldValue>(*this);
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
    out << "TensorDataType(" << _tensorType << ")";
}

std::unique_ptr<const TensorDataType>
TensorDataType::fromSpec(const vespalib::string &spec)
{
    return std::make_unique<const TensorDataType>(ValueType::from_spec(spec));
}

} // document
