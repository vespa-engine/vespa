// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_modify_update.h"
#include "tensor_partial_update.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/base/field.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/document/util/serializableexceptions.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/xmlstream.h>
#include <ostream>

using vespalib::IllegalArgumentException;
using vespalib::IllegalStateException;
using vespalib::make_string;
using vespalib::eval::ValueType;
using vespalib::eval::CellType;
using vespalib::eval::FastValueBuilderFactory;

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
      _tensor()
{
}

TensorModifyUpdate::TensorModifyUpdate(Operation operation, std::unique_ptr<TensorFieldValue> tensor)
    : ValueUpdate(TensorModify),
      TensorUpdate(),
      _operation(operation),
      _tensorType(std::make_unique<TensorDataType>(dynamic_cast<const TensorDataType &>(*tensor->getDataType()))),
      _tensor(static_cast<TensorFieldValue *>(_tensorType->createFieldValue().release()))
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

std::unique_ptr<vespalib::eval::Value>
TensorModifyUpdate::applyTo(const vespalib::eval::Value &tensor) const
{
    return apply_to(tensor, FastValueBuilderFactory::get());
}

std::unique_ptr<vespalib::eval::Value>
TensorModifyUpdate::apply_to(const Value &old_tensor,
                             const ValueBuilderFactory &factory) const
{
    if (auto cellsTensor = _tensor->getAsTensorPtr()) {
        auto op = getJoinFunction(_operation);
        return TensorPartialUpdate::modify(old_tensor, op, *cellsTensor, factory);
    }
    return {};
}

bool
TensorModifyUpdate::applyTo(FieldValue& value) const
{
    if (value.isA(FieldValue::Type::TENSOR)) {
        TensorFieldValue &tensorFieldValue = static_cast<TensorFieldValue &>(value);
        auto oldTensor = tensorFieldValue.getAsTensorPtr();
        if (oldTensor) {
            auto newTensor = applyTo(*oldTensor);
            if (newTensor) {
                tensorFieldValue = std::move(newTensor);
            }
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
