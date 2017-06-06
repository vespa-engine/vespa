// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensorfieldvalue.h"
#include <vespa/document/datatype/datatype.h>
#include <vespa/eval/tensor/tensor.h>
#include <ostream>
#include <cassert>

using vespalib::tensor::Tensor;

namespace document {

TensorFieldValue::TensorFieldValue()
    : FieldValue(),
      _tensor(),
      _altered(true)
{
}

TensorFieldValue::TensorFieldValue(const TensorFieldValue &rhs)
    : FieldValue(),
      _tensor(),
      _altered(true)
{
    if (rhs._tensor) {
        _tensor = rhs._tensor->clone();
    }
}


TensorFieldValue::TensorFieldValue(TensorFieldValue &&rhs)
    : FieldValue(),
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
        if (rhs._tensor) {
            _tensor = rhs._tensor->clone();
        } else {
            _tensor.reset();
        }
        _altered = true;
    }
    return *this;
}


TensorFieldValue &
TensorFieldValue::operator=(std::unique_ptr<Tensor> rhs)
{
    _tensor = std::move(rhs);
    _altered = true;
    return *this;
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
    return DataType::TENSOR;
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
    out << "{TensorFieldValue::print not yet implemented}";
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
    _tensor = std::move(rhs);
    _altered = false; // Serialized form already exists
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
