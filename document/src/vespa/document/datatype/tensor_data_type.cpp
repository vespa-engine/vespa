// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_data_type.h"
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/vespalib/util/exceptions.h>
#include <sstream>

using vespalib::eval::ValueType;

namespace document {

TensorDataType::TensorDataType(ValueType tensorType)
    : PrimitiveDataType(DataType::T_TENSOR),
      _tensorType(std::move(tensorType))
{
}

TensorDataType::TensorDataType(const TensorDataType &) = default;
TensorDataType::~TensorDataType() = default;

bool
TensorDataType::equals(const DataType& other) const noexcept
{
    if (!DataType::equals(other)) {
        return false;
    }
    return _tensorType == other.cast_tensor()->_tensorType;
}

FieldValue::UP
TensorDataType::createFieldValue() const
{
    return std::make_unique<TensorFieldValue>(*this);
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

bool
TensorDataType::isAssignableType(const ValueType &tensorType) const
{
    return isAssignableType(_tensorType, tensorType);
}

bool
TensorDataType::isAssignableType(const ValueType &fieldTensorType, const ValueType &tensorType)
{
    if (fieldTensorType.is_error()) {
        return false;
    }
    return (fieldTensorType == tensorType);
}

} // document
