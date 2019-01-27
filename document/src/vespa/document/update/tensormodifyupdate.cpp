// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensormodifyupdate.h"
#include <vespa/document/base/field.h>
#include <vespa/document/base/exceptions.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/document/util/serializableexceptions.h>
#include <vespa/eval/tensor/serialization/typed_binary_format.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/xmlstream.h>
#include <ostream>

using vespalib::IllegalArgumentException;
using vespalib::IllegalStateException;
using vespalib::tensor::TypedBinaryFormat;
using vespalib::tensor::Tensor;
using vespalib::make_string;

namespace document {

IMPLEMENT_IDENTIFIABLE(TensorModifyUpdate, ValueUpdate);

TensorModifyUpdate::TensorModifyUpdate()
    : _operator(Operator::MAX_NUM_OPERATORS),
      _operand()
{
}

TensorModifyUpdate::TensorModifyUpdate(const TensorModifyUpdate &rhs)
    : _operator(rhs._operator),
      _operand(rhs._operand->clone())
{
}

TensorModifyUpdate::TensorModifyUpdate(Operator op, std::unique_ptr<vespalib::tensor::Tensor> &&operand)
    : _operator(op),
      _operand(std::move(operand))
{
}

TensorModifyUpdate::~TensorModifyUpdate() = default;

TensorModifyUpdate &
TensorModifyUpdate::operator=(const TensorModifyUpdate &rhs)
{
    _operator = rhs._operator;
    _operand = rhs._operand->clone();
    return *this;
}

TensorModifyUpdate &
TensorModifyUpdate::operator=(TensorModifyUpdate &&rhs)
{
    _operator = rhs._operator;
    _operand = std::move(rhs._operand);
    return *this;
}

bool
TensorModifyUpdate::operator==(const ValueUpdate &other) const
{
    if (other.getClass().id() != TensorModifyUpdate::classId) return false;
    const TensorModifyUpdate& o(static_cast<const TensorModifyUpdate&>(other));
    if (_operator != o._operator) return false;
    if (!_operand->equals(*o._operand)) return false;
    return true;
}


void
TensorModifyUpdate::checkCompatibility(const Field& field) const
{
    if (field.getDataType() != *DataType::TENSOR) {
        throw IllegalArgumentException(make_string(
                "Can not perform tensor modify update on non-tensor field '%s'.",
                field.getName().data()), VESPA_STRLOC);
    }
}

bool
TensorModifyUpdate::applyTo(FieldValue& value) const
{
    if (value.inherits(TensorFieldValue::classId)) {
        TensorFieldValue &tensorFieldValue = static_cast<TensorFieldValue &>(value);
        auto &oldTensor = tensorFieldValue.getAsTensorPtr();
        // TODO: Apply operator with operand
        auto newTensor = oldTensor->clone();
        tensorFieldValue = std::move(newTensor);
    } else {
        std::string err = make_string(
                "Unable to perform a tensor modify update on a \"%s\" field "
                "value.", value.getClass().name());
        throw IllegalStateException(err, VESPA_STRLOC);
    }
    return true;
}

void
TensorModifyUpdate::printXml(XmlOutputStream& xos) const
{
    xos << "{TensorModifyUpdate::printXml not yet implemented}";
}

void
TensorModifyUpdate::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    (void) verbose;
    (void) indent;
    out << "{TensorModifyUpdate::print not yet implemented}";
}

void
TensorModifyUpdate::deserialize(const DocumentTypeRepo&, const DataType&, nbostream & stream)
{
    uint8_t op;
    stream >> op;
    if (op >= static_cast<uint8_t>(Operator::MAX_NUM_OPERATORS)) {
        vespalib::asciistream msg;
        msg << "Unrecognized tensor modify update operation " << static_cast<uint32_t>(op);
        throw DeserializeException(msg.str(), VESPA_STRLOC);
    }
    _operator = static_cast<Operator>(op);
    auto tensor = TypedBinaryFormat::deserialize(stream);
    _operand = std::move(tensor);
}

TensorModifyUpdate*
TensorModifyUpdate::clone() const
{
    return new TensorModifyUpdate(*this);
}

}
