// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_remove_update.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/eval/tensor/cell_values.h>
#include <vespa/eval/tensor/sparse/sparse_tensor.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/xmlstream.h>
#include <ostream>

using vespalib::IllegalArgumentException;
using vespalib::IllegalStateException;
using vespalib::tensor::Tensor;
using vespalib::make_string;
using vespalib::eval::ValueType;

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
    return std::make_unique<const TensorDataType>(ValueType::tensor_type(std::move(list), tensorType.getTensorType().cell_type()));
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

std::unique_ptr<Tensor>
TensorRemoveUpdate::applyTo(const Tensor &tensor) const
{
    auto &addressTensor = _tensor->getAsTensorPtr();
    if (addressTensor) {
        // Address tensor being sparse was validated during deserialize().
        vespalib::tensor::CellValues cellAddresses(static_cast<const vespalib::tensor::SparseTensor &>(*addressTensor));
        return tensor.remove(cellAddresses);
    }
    return std::unique_ptr<Tensor>();
}

bool
TensorRemoveUpdate::applyTo(FieldValue &value) const
{
    if (value.inherits(TensorFieldValue::classId)) {
        TensorFieldValue &tensorFieldValue = static_cast<TensorFieldValue &>(value);
        auto &oldTensor = tensorFieldValue.getAsTensorPtr();
        auto newTensor = applyTo(*oldTensor);
        if (newTensor) {
            tensorFieldValue = std::move(newTensor);
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
verifyAddressTensorIsSparse(const std::unique_ptr<Tensor> &addressTensor)
{
    if (addressTensor && !dynamic_cast<const vespalib::tensor::SparseTensor *>(addressTensor.get())) {
        vespalib::string err = make_string("Expected address tensor to be sparse, but has type '%s'",
                                           addressTensor->type().to_spec().c_str());
        throw IllegalStateException(err, VESPA_STRLOC);
    }
}

}

void
TensorRemoveUpdate::deserialize(const DocumentTypeRepo &repo, const DataType &type, nbostream &stream)
{
    _tensorType = convertToCompatibleType(Identifiable::cast<const TensorDataType &>(type));
    auto tensor = _tensorType->createFieldValue();
    if (tensor->inherits(TensorFieldValue::classId)) {
        _tensor.reset(static_cast<TensorFieldValue *>(tensor.release()));
    } else {
        vespalib::string err = make_string("Expected tensor field value, got a '%s' field value",
                                           tensor->getClass().name());
        throw IllegalStateException(err, VESPA_STRLOC);
    }
    VespaDocumentDeserializer deserializer(repo, stream, Document::getNewestSerializationVersion());
    deserializer.read(*_tensor);
    verifyAddressTensorIsSparse(_tensor->getAsTensorPtr());
}

TensorRemoveUpdate *
TensorRemoveUpdate::clone() const
{
    return new TensorRemoveUpdate(*this);
}

}
