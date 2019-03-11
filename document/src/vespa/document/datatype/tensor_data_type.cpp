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

TensorDataType::~TensorDataType() = default;

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

bool
TensorDataType::isAssignableType(const ValueType &tensorType) const
{
    return isAssignableType(_tensorType, tensorType);
}

bool
TensorDataType::isAssignableType(const ValueType &fieldTensorType, const ValueType &tensorType)
{
    const auto &dimensions = fieldTensorType.dimensions();
    const auto &rhsDimensions = tensorType.dimensions();
    if (!tensorType.is_tensor() || dimensions.size() != rhsDimensions.size()) {
        return false;
    }
    for (size_t i = 0; i < dimensions.size(); ++i) {
        const auto &dim = dimensions[i];
        const auto &rhsDim = rhsDimensions[i];
        if ((dim.name != rhsDim.name) ||
            (dim.is_indexed() != rhsDim.is_indexed()) ||
            (rhsDim.is_indexed() && !rhsDim.is_bound()) || 
            (dim.is_bound() && (dim.size != rhsDim.size))) {
            return false;
        }
    }
    return true;
}

} // document
