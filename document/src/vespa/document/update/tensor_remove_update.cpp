// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_remove_update.h"
#include "tensor_partial_update.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/xmlstream.h>
#include <ostream>
#include <cassert>

using vespalib::IllegalArgumentException;
using vespalib::IllegalStateException;
using vespalib::make_string;
using vespalib::eval::Value;
using vespalib::eval::ValueType;
using vespalib::eval::CellType;
using vespalib::eval::FastValueBuilderFactory;

namespace document {

namespace {

std::unique_ptr<const TensorDataType>
convertToCompatibleType(const TensorDataType &tensorType)
{
    std::vector<ValueType::Dimension> list;
    for (const auto &dim : tensorType.getTensorType().dimensions()) {
        if (dim.is_mapped()) {
            list.emplace_back(dim.name);
        }
    }
    return std::make_unique<const TensorDataType>(ValueType::make_type(tensorType.getTensorType().cell_type(), std::move(list)));
}

}

IMPLEMENT_IDENTIFIABLE(TensorRemoveUpdate, ValueUpdate);

TensorRemoveUpdate::TensorRemoveUpdate()
    : _tensorType(),
      _tensor()
{
}

TensorRemoveUpdate::TensorRemoveUpdate(const TensorRemoveUpdate &rhs)
    : _tensorType(rhs._tensorType->clone()),
      _tensor(rhs._tensor->clone())
{
}

TensorRemoveUpdate::TensorRemoveUpdate(std::unique_ptr<TensorFieldValue> tensor)
    : _tensorType(Identifiable::cast<const TensorDataType &>(*tensor->getDataType()).clone()),
      _tensor(Identifiable::cast<TensorFieldValue *>(_tensorType->createFieldValue().release()))
{
    *_tensor = *tensor;
}

TensorRemoveUpdate::~TensorRemoveUpdate() = default;

TensorRemoveUpdate &
TensorRemoveUpdate::operator=(const TensorRemoveUpdate &rhs)
{
    if (&rhs != this) {
        _tensor.reset();
        _tensorType.reset(rhs._tensorType->clone());
        _tensor.reset(Identifiable::cast<TensorFieldValue *>(_tensorType->createFieldValue().release()));
        *_tensor = *rhs._tensor;
    }
    return *this;
}

TensorRemoveUpdate &
TensorRemoveUpdate::operator=(TensorRemoveUpdate &&rhs)
{
    _tensorType = std::move(rhs._tensorType);
    _tensor = std::move(rhs._tensor);
    return *this;
}

bool
TensorRemoveUpdate::operator==(const ValueUpdate &other) const
{
    if (other.getClass().id() != TensorRemoveUpdate::classId) {
        return false;
    }
    const TensorRemoveUpdate& o(static_cast<const TensorRemoveUpdate&>(other));
    if (*_tensor != *o._tensor) {
        return false;
    }
    return true;
}

void
TensorRemoveUpdate::checkCompatibility(const Field &field) const
{
    if (field.getDataType().getClass().id() != TensorDataType::classId) {
        throw IllegalArgumentException(make_string("Cannot perform tensor remove update on non-tensor field '%s'",
                                                   field.getName().data()), VESPA_STRLOC);
    }
}

std::unique_ptr<vespalib::eval::Value>
TensorRemoveUpdate::applyTo(const vespalib::eval::Value &tensor) const
{
    return apply_to(tensor, FastValueBuilderFactory::get());
}

std::unique_ptr<vespalib::eval::Value>
TensorRemoveUpdate::apply_to(const Value &old_tensor,
                             const ValueBuilderFactory &factory) const
{
    if (auto addressTensor = _tensor->getAsTensorPtr()) {
        return TensorPartialUpdate::remove(old_tensor, *addressTensor, factory);
    }
    return {};
}

bool
TensorRemoveUpdate::applyTo(FieldValue &value) const
{
    if (value.inherits(TensorFieldValue::classId)) {
        TensorFieldValue &tensorFieldValue = static_cast<TensorFieldValue &>(value);
        auto oldTensor = tensorFieldValue.getAsTensorPtr();
        if (oldTensor) {
            auto newTensor = applyTo(*oldTensor);
            if (newTensor) {
                tensorFieldValue = std::move(newTensor);
            }
        }
    } else {
        vespalib::string err = make_string("Unable to perform a tensor remove update on a '%s' field value",
                                           value.getClass().name());
        throw IllegalStateException(err, VESPA_STRLOC);
    }
    return true;
}

void
TensorRemoveUpdate::printXml(XmlOutputStream &xos) const
{
    xos << "{TensorRemoveUpdate::printXml not yet implemented}";
}

void
TensorRemoveUpdate::print(std::ostream &out, bool verbose, const std::string &indent) const
{
    out << indent << "TensorRemoveUpdate(";
    if (_tensor) {
        _tensor->print(out, verbose, indent);
    }
    out << ")";
}

namespace {

void
verifyAddressTensorIsSparse(const Value *addressTensor)
{
    if (addressTensor == nullptr) {
        throw IllegalStateException("Address tensor is not set", VESPA_STRLOC);
    }
    if (addressTensor->type().is_sparse()) {
        return;
    }
    auto err = make_string("Expected address tensor to be sparse, but has type '%s'",
                           addressTensor->type().to_spec().c_str());
    throw IllegalStateException(err, VESPA_STRLOC);
}

void
verify_tensor_type_dimensions_are_subset_of(const ValueType& lhs_type,
                                            const ValueType& rhs_type)
{
    for (const auto& dim : lhs_type.dimensions()) {
        if (rhs_type.dimension_index(dim.name) == ValueType::Dimension::npos) {
            auto err = make_string("Unexpected type '%s' for address tensor. "
                                   "Expected dimensions to be a subset of '%s'",
                                   lhs_type.to_spec().c_str(), rhs_type.to_spec().c_str());
            throw IllegalStateException(err, VESPA_STRLOC);
        }
    }
}

}

void
TensorRemoveUpdate::deserialize(const DocumentTypeRepo &repo, const DataType &type, nbostream &stream)
{
    VespaDocumentDeserializer deserializer(repo, stream, Document::getNewestSerializationVersion());
    auto tensor = deserializer.readTensor();
    verifyAddressTensorIsSparse(tensor.get());
    auto compatible_type = convertToCompatibleType(Identifiable::cast<const TensorDataType &>(type));
    verify_tensor_type_dimensions_are_subset_of(tensor->type(), compatible_type->getTensorType());
    _tensorType = std::make_unique<const TensorDataType>(tensor->type());
    _tensor = std::make_unique<TensorFieldValue>(*_tensorType);
    _tensor->assignDeserialized(std::move(tensor));
}

TensorRemoveUpdate *
TensorRemoveUpdate::clone() const
{
    return new TensorRemoveUpdate(*this);
}

}
