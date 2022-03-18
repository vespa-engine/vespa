// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensorfieldvalue.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/vespalib/util/xmlstream.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/value.h>
#include <ostream>

using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::TensorSpec;
using vespalib::eval::ValueType;
using namespace vespalib::xml;

namespace document {

namespace {

TensorDataType emptyTensorDataType(ValueType::error_type());

vespalib::string makeWrongTensorTypeMsg(const ValueType &fieldTensorType, const ValueType &tensorType)
{
    return vespalib::make_string("Field tensor type is '%s' but other tensor type is '%s'",
                                 fieldTensorType.to_spec().c_str(),
                                 tensorType.to_spec().c_str());
}

}

TensorFieldValue::TensorFieldValue()
    : TensorFieldValue(emptyTensorDataType)
{
}

TensorFieldValue::TensorFieldValue(const TensorDataType &dataType)
    : FieldValue(Type::TENSOR),
      _dataType(dataType),
      _tensor()
{
}

TensorFieldValue::TensorFieldValue(const TensorFieldValue &rhs)
    : FieldValue(Type::TENSOR),
      _dataType(rhs._dataType),
      _tensor()
{
    if (rhs._tensor) {
        _tensor = FastValueBuilderFactory::get().copy(*rhs._tensor);
    }
}


TensorFieldValue::TensorFieldValue(TensorFieldValue &&rhs)
    : FieldValue(Type::TENSOR),
      _dataType(rhs._dataType),
      _tensor()
{
    _tensor = std::move(rhs._tensor);
}


TensorFieldValue::~TensorFieldValue()
{
}


TensorFieldValue &
TensorFieldValue::operator=(const TensorFieldValue &rhs)
{
    if (this != &rhs) {
        if (&_dataType == &rhs._dataType || !rhs._tensor ||
            _dataType.isAssignableType(rhs._tensor->type())) {
            if (rhs._tensor) {
                _tensor = FastValueBuilderFactory::get().copy(*rhs._tensor);
            } else {
                _tensor.reset();
            }
        } else {
            throw WrongTensorTypeException(makeWrongTensorTypeMsg(_dataType.getTensorType(), rhs._tensor->type()), VESPA_STRLOC);
        }
    }
    return *this;
}


TensorFieldValue &
TensorFieldValue::operator=(std::unique_ptr<vespalib::eval::Value> rhs)
{
    if (!rhs || _dataType.isAssignableType(rhs->type())) {
        _tensor = std::move(rhs);
    } else {
        throw WrongTensorTypeException(makeWrongTensorTypeMsg(_dataType.getTensorType(), rhs->type()), VESPA_STRLOC);
    }
    return *this;
}


void
TensorFieldValue::make_empty_if_not_existing()
{
    if (!_tensor) {
        TensorSpec empty_spec(_dataType.getTensorType().to_spec());
        _tensor = value_from_spec(empty_spec, FastValueBuilderFactory::get());
    }
}


void
TensorFieldValue::accept(FieldValueVisitor &visitor)
{
    visitor.visit(*this);
}


void
TensorFieldValue::accept(ConstFieldValueVisitor &visitor) const
{
    visitor.visit(*this);
}


const DataType *
TensorFieldValue::getDataType() const
{
    return &_dataType;
}

TensorFieldValue*
TensorFieldValue::clone() const
{
    return new TensorFieldValue(*this);
}


void
TensorFieldValue::print(std::ostream& out, bool verbose,
                        const std::string& indent) const
{
    (void) verbose;
    (void) indent;
    out << "{TensorFieldValue: ";
    if (_tensor) {
        out << spec_from_value(*_tensor).to_string();
    } else {
        out << "null";
    }
    out << "}";
}

void
TensorFieldValue::printXml(XmlOutputStream& out) const
{
    out << "{TensorFieldValue::printXml not yet implemented}";
}


FieldValue &
TensorFieldValue::assign(const FieldValue &value)
{
    if (value.isA(Type::TENSOR)) {
        const auto * rhs = static_cast<const TensorFieldValue *>(&value);
        *this = *rhs;
    } else {
        return FieldValue::assign(value);
    }
    return *this;
}


void
TensorFieldValue::assignDeserialized(std::unique_ptr<vespalib::eval::Value> rhs)
{
    if (!rhs || _dataType.isAssignableType(rhs->type())) {
        _tensor = std::move(rhs);
    } else {
        throw WrongTensorTypeException(makeWrongTensorTypeMsg(_dataType.getTensorType(), rhs->type()), VESPA_STRLOC);
    }
}


int
TensorFieldValue::compare(const FieldValue &other) const
{
    if (this == &other) {
        return 0;	// same identity
    }
    int diff = FieldValue::compare(other);
    if (diff != 0) {
        return diff;    // field type mismatch
    }
    const TensorFieldValue & rhs(static_cast<const TensorFieldValue &>(other));
    if (!_tensor) {
        return (rhs._tensor ? -1 : 0);
    }
    if (!rhs._tensor) {
        return 1;
    }
    // equal pointers always means identical
    if (_tensor.get() == rhs._tensor.get()) {
        return 0;
    }
    // compare just the type first:
    auto lhs_type = _tensor->type().to_spec();
    auto rhs_type = rhs._tensor->type().to_spec();
    int type_cmp = lhs_type.compare(rhs_type);
    if (type_cmp != 0) {
        return type_cmp;
    }
    // Compare the actual tensors by converting to TensorSpec strings.
    // TODO: this can be very slow, check if it might be used for anything
    // performance-critical.
    auto lhs_spec = spec_from_value(*_tensor).to_string();
    auto rhs_spec = spec_from_value(*rhs._tensor).to_string();
    return lhs_spec.compare(rhs_spec);
}

} // document
