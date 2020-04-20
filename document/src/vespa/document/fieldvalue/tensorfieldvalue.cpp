// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensorfieldvalue.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/vespalib/util/xmlstream.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <ostream>
#include <cassert>

using vespalib::tensor::Tensor;
using vespalib::eval::TensorSpec;
using vespalib::eval::ValueType;
using Engine = vespalib::tensor::DefaultTensorEngine;
using namespace vespalib::xml;

namespace document {

namespace {

TensorDataType emptyTensorDataType;

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
    : FieldValue(),
      _dataType(dataType),
      _tensor(),
      _altered(true)
{
}

TensorFieldValue::TensorFieldValue(const TensorFieldValue &rhs)
    : FieldValue(),
      _dataType(rhs._dataType),
      _tensor(),
      _altered(true)
{
    if (rhs._tensor) {
        _tensor = rhs._tensor->clone();
    }
}


TensorFieldValue::TensorFieldValue(TensorFieldValue &&rhs)
    : FieldValue(),
      _dataType(rhs._dataType),
      _tensor(),
      _altered(true)
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
                _tensor = rhs._tensor->clone();
            } else {
                _tensor.reset();
            }
            _altered = true;
        } else {
            throw WrongTensorTypeException(makeWrongTensorTypeMsg(_dataType.getTensorType(), rhs._tensor->type()), VESPA_STRLOC);
        }
    }
    return *this;
}


TensorFieldValue &
TensorFieldValue::operator=(std::unique_ptr<Tensor> rhs)
{
    if (!rhs || _dataType.isAssignableType(rhs->type())) {
        _tensor = std::move(rhs);
        _altered = true;
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
        auto empty_value = Engine::ref().from_spec(empty_spec);
        auto tensor_ptr = dynamic_cast<Tensor*>(empty_value.get());
        assert(tensor_ptr != nullptr);
        _tensor.reset(tensor_ptr);
        empty_value.release();
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


bool
TensorFieldValue::hasChanged() const
{
    return _altered;
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
        out << Engine::ref().to_spec(*_tensor).to_string();
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
    const TensorFieldValue *rhs =
        Identifiable::cast<const TensorFieldValue *>(&value);
    if (rhs != nullptr) {
        *this = *rhs;
    } else {
        return FieldValue::assign(value);
    }
    return *this;
}


void
TensorFieldValue::assignDeserialized(std::unique_ptr<Tensor> rhs)
{
    if (!rhs || _dataType.isAssignableType(rhs->type())) {
        _tensor = std::move(rhs);
        _altered = false; // Serialized form already exists
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
    if (_tensor->equals(*rhs._tensor)) {
        return 0;
    }
    assert(_tensor.get() != rhs._tensor.get());
    // XXX: Wrong, compares identity of tensors instead of values
    // Note: sorting can be dangerous due to this.
    return ((_tensor.get()  < rhs._tensor.get()) ? -1 : 1);
}

IMPLEMENT_IDENTIFIABLE(TensorFieldValue, FieldValue);

} // document
