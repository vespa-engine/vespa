// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensormodifyupdate.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/base/field.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/document/util/serializableexceptions.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/tensor/cell_values.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/xmlstream.h>
#include <ostream>

using vespalib::IllegalArgumentException;
using vespalib::IllegalStateException;
using vespalib::tensor::Tensor;
using vespalib::make_string;

using join_fun_t = double (*)(double, double);

namespace document {

namespace {

double
replace(double, double b)
{
    return b;
}
    
join_fun_t
getJoinFunction(TensorModifyUpdate::Operation operation)
{
    using Operation = TensorModifyUpdate::Operation;
    
    switch (operation) {
    case Operation::REPLACE:
        return replace;
    case Operation::ADD:
        return vespalib::eval::operation::Add::f;
    case Operation::MUL:
        return vespalib::eval::operation::Mul::f;
    default:
        throw IllegalArgumentException("Bad operation", VESPA_STRLOC);
    }
}

vespalib::string
getJoinFunctionName(TensorModifyUpdate::Operation operation)
{
    using Operation = TensorModifyUpdate::Operation;
    
    switch (operation) {
    case Operation::REPLACE:
        return "replace";
    case Operation::ADD:
        return "add";
    case Operation::MUL:
        return "multiply";
    default:
        throw IllegalArgumentException("Bad operation", VESPA_STRLOC);
    }
}

}

IMPLEMENT_IDENTIFIABLE(TensorModifyUpdate, ValueUpdate);

TensorModifyUpdate::TensorModifyUpdate()
    : _operation(Operation::MAX_NUM_OPERATIONS),
      _tensor()
{
}

TensorModifyUpdate::TensorModifyUpdate(const TensorModifyUpdate &rhs)
    : _operation(rhs._operation),
      _tensor(rhs._tensor->clone())
{
}

TensorModifyUpdate::TensorModifyUpdate(Operation operation, std::unique_ptr<TensorFieldValue> &&tensor)
    : _operation(operation),
      _tensor(std::move(tensor))
{
}

TensorModifyUpdate::~TensorModifyUpdate() = default;

TensorModifyUpdate &
TensorModifyUpdate::operator=(const TensorModifyUpdate &rhs)
{
    _operation = rhs._operation;
    _tensor.reset(rhs._tensor->clone());
    return *this;
}

TensorModifyUpdate &
TensorModifyUpdate::operator=(TensorModifyUpdate &&rhs)
{
    _operation = rhs._operation;
    _tensor = std::move(rhs._tensor);
    return *this;
}

bool
TensorModifyUpdate::operator==(const ValueUpdate &other) const
{
    if (other.getClass().id() != TensorModifyUpdate::classId) {
        return false;
    }
    const TensorModifyUpdate& o(static_cast<const TensorModifyUpdate&>(other));
    if (_operation != o._operation) {
        return false;
    }
    if (*_tensor != *o._tensor) {
        return false;
    }
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

std::unique_ptr<Tensor>
TensorModifyUpdate::applyTo(const Tensor &tensor) const
{
    auto &cellTensor = _tensor->getAsTensorPtr();
    if (cellTensor) {
        vespalib::tensor::CellValues cellValues(static_cast<const vespalib::tensor::SparseTensor &>(*cellTensor));
        return tensor.modify(getJoinFunction(_operation), cellValues);
    }
    return std::unique_ptr<Tensor>();
}

bool
TensorModifyUpdate::applyTo(FieldValue& value) const
{
    if (value.inherits(TensorFieldValue::classId)) {
        TensorFieldValue &tensorFieldValue = static_cast<TensorFieldValue &>(value);
        auto &oldTensor = tensorFieldValue.getAsTensorPtr();
        auto newTensor = applyTo(*oldTensor);
        if (newTensor) {
            tensorFieldValue = std::move(newTensor);
        }
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
    out << indent << "TensorModifyUpdate(" << getJoinFunctionName(_operation) << ",";
    if (_tensor) {
        _tensor->print(out, verbose, indent);
    }
    out << ")";
}

void
TensorModifyUpdate::deserialize(const DocumentTypeRepo &repo, const DataType &type, nbostream & stream)
{
    uint8_t op;
    stream >> op;
    if (op >= static_cast<uint8_t>(Operation::MAX_NUM_OPERATIONS)) {
        vespalib::asciistream msg;
        msg << "Unrecognized tensor modify update operation " << static_cast<uint32_t>(op);
        throw DeserializeException(msg.str(), VESPA_STRLOC);
    }
    _operation = static_cast<Operation>(op);
    auto tensor = type.createFieldValue();
    if (tensor->inherits(TensorFieldValue::classId)) {
        _tensor.reset(static_cast<TensorFieldValue *>(tensor.release()));
    } else {
        std::string err = make_string(
                "Expected tensor field value, got a \"%s\" field "
                "value.", tensor->getClass().name());
        throw IllegalStateException(err, VESPA_STRLOC);
    }
    VespaDocumentDeserializer deserializer(repo, stream, Document::getNewestSerializationVersion());
    deserializer.read(*_tensor);
}

TensorModifyUpdate*
TensorModifyUpdate::clone() const
{
    return new TensorModifyUpdate(*this);
}

}
