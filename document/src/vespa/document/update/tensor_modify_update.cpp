// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_modify_update.h"
#include "tensor_partial_update.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/base/field.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/document/util/serializableexceptions.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/xmlstream.h>
#include <ostream>

using vespalib::IllegalArgumentException;
using vespalib::IllegalStateException;
using vespalib::eval::CellType;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::Value;
using vespalib::eval::ValueType;
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
    case Operation::MULTIPLY:
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
    case Operation::MULTIPLY:
        return "multiply";
    default:
        throw IllegalArgumentException("Bad operation", VESPA_STRLOC);
    }
}

std::unique_ptr<const TensorDataType>
convertToCompatibleType(const TensorDataType &tensorType)
{
    std::vector<ValueType::Dimension> list;
    for (const auto &dim : tensorType.getTensorType().dimensions()) {
        list.emplace_back(dim.name);
    }
    return std::make_unique<const TensorDataType>(ValueType::make_type(tensorType.getTensorType().cell_type(), std::move(list)));
}

}

TensorModifyUpdate::TensorModifyUpdate()
    : ValueUpdate(TensorModify),
      TensorUpdate(),
      _operation(Operation::MAX_NUM_OPERATIONS),
      _tensorType(),
      _tensor(),
      _default_cell_value()
{
}

TensorModifyUpdate::TensorModifyUpdate(Operation operation, std::unique_ptr<TensorFieldValue> tensor)
    : ValueUpdate(TensorModify),
      TensorUpdate(),
      _operation(operation),
      _tensorType(std::make_unique<TensorDataType>(dynamic_cast<const TensorDataType &>(*tensor->getDataType()))),
      _tensor(static_cast<TensorFieldValue *>(_tensorType->createFieldValue().release())),
      _default_cell_value()
{
    *_tensor = *tensor;
}

TensorModifyUpdate::TensorModifyUpdate(Operation operation, std::unique_ptr<TensorFieldValue> tensor, double default_cell_value)
    : ValueUpdate(TensorModify),
      TensorUpdate(),
      _operation(operation),
      _tensorType(std::make_unique<TensorDataType>(dynamic_cast<const TensorDataType &>(*tensor->getDataType()))),
      _tensor(static_cast<TensorFieldValue *>(_tensorType->createFieldValue().release())),
      _default_cell_value(default_cell_value)
{
    *_tensor = *tensor;
}

TensorModifyUpdate::~TensorModifyUpdate() = default;

bool
TensorModifyUpdate::operator==(const ValueUpdate &other) const
{
    if (other.getType() != TensorModify) {
        return false;
    }
    const TensorModifyUpdate& o(static_cast<const TensorModifyUpdate&>(other));
    if (_operation != o._operation) {
        return false;
    }
    if (*_tensor != *o._tensor) {
        return false;
    }
    if (_default_cell_value != o._default_cell_value) {
        return false;
    }
    return true;
}


void
TensorModifyUpdate::checkCompatibility(const Field& field) const
{
    if ( ! field.getDataType().isTensor()) {
        throw IllegalArgumentException(make_string("Cannot perform tensor modify update on non-tensor field '%s'",
                                                   field.getName().data()), VESPA_STRLOC);
    }
}

std::unique_ptr<Value>
TensorModifyUpdate::applyTo(const Value &tensor) const
{
    return apply_to(tensor, FastValueBuilderFactory::get());
}

std::unique_ptr<Value>
TensorModifyUpdate::apply_to(const Value &old_tensor,
                             const ValueBuilderFactory &factory) const
{
    if (auto cellsTensor = _tensor->getAsTensorPtr()) {
        auto op = getJoinFunction(_operation);
        if (_default_cell_value.has_value()) {
            return TensorPartialUpdate::modify_with_defaults(old_tensor, op, *cellsTensor, _default_cell_value.value(), factory);
        } else {
            return TensorPartialUpdate::modify(old_tensor, op, *cellsTensor, factory);
        }
    }
    return {};
}

namespace {

std::unique_ptr<Value>
create_empty_tensor(const ValueType& type)
{
    const auto& factory = FastValueBuilderFactory::get();
    vespalib::eval::TensorSpec empty_spec(type.to_spec());
    return vespalib::eval::value_from_spec(empty_spec, factory);
}

}

bool
TensorModifyUpdate::applyTo(FieldValue& value) const
{
    if (value.isA(FieldValue::Type::TENSOR)) {
        TensorFieldValue &tensorFieldValue = static_cast<TensorFieldValue &>(value);
        auto old_tensor = tensorFieldValue.getAsTensorPtr();
        std::unique_ptr<Value> new_tensor;
        if (old_tensor) {
            new_tensor = applyTo(*old_tensor);
        } else if (_default_cell_value.has_value()) {
            auto empty_tensor = create_empty_tensor(tensorFieldValue.get_tensor_data_type().getTensorType());
            new_tensor = applyTo(*empty_tensor);
        }
        if (new_tensor) {
            tensorFieldValue = std::move(new_tensor);
        }
    } else {
        vespalib::string err = make_string("Unable to perform a tensor modify update on a '%s' field value",
                                           value.className());
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
    if (_default_cell_value.has_value()) {
        out << "," << _default_cell_value.value();
    }
    out << ")";
}

namespace {

void
verifyCellsTensorIsSparse(const vespalib::eval::Value *cellsTensor)
{
    if (cellsTensor == nullptr) {
        return;
    }
    if (cellsTensor->type().is_sparse()) {
        return;
    }
    vespalib::string err = make_string("Expected cells tensor to be sparse, but has type '%s'",
                                       cellsTensor->type().to_spec().c_str());
    throw IllegalStateException(err, VESPA_STRLOC);
}

TensorModifyUpdate::Operation
decode_operation(uint8_t encoded_op)
{
    uint8_t OP_MASK = 0b01111111;
    uint8_t op = encoded_op & OP_MASK;
    if (op >= static_cast<uint8_t>(TensorModifyUpdate::Operation::MAX_NUM_OPERATIONS)) {
        vespalib::asciistream msg;
        msg << "Unrecognized tensor modify update operation " << static_cast<uint32_t>(op);
        throw DeserializeException(msg.str(), VESPA_STRLOC);
    }
    return static_cast<TensorModifyUpdate::Operation>(op);
}

bool
decode_create_non_existing_cells(uint8_t encoded_op)
{
    uint8_t CREATE_FLAG = 0b10000000;
    return (encoded_op & CREATE_FLAG) != 0;
}

}

void
TensorModifyUpdate::deserialize(const DocumentTypeRepo &repo, const DataType &type, nbostream & stream)
{
    uint8_t op;
    stream >> op;
    _operation = decode_operation(op);
    if (decode_create_non_existing_cells(op)) {
        double value;
        stream >> value;
        _default_cell_value = value;
    }
    _tensorType = convertToCompatibleType(dynamic_cast<const TensorDataType &>(type));
    auto tensor = _tensorType->createFieldValue();
    if (tensor->isA(FieldValue::Type::TENSOR)) {
        _tensor.reset(static_cast<TensorFieldValue *>(tensor.release()));
    } else {
        vespalib::string err = make_string("Expected tensor field value, got a '%s' field value",
                                           tensor->className());
        throw IllegalStateException(err, VESPA_STRLOC);
    }
    VespaDocumentDeserializer deserializer(repo, stream, Document::getNewestSerializationVersion());
    deserializer.read(*_tensor);
    verifyCellsTensorIsSparse(_tensor->getAsTensorPtr());
}

}
