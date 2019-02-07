// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensoraddupdate.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/base/field.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/document/util/serializableexceptions.h>
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

namespace document {

IMPLEMENT_IDENTIFIABLE(TensorAddUpdate, ValueUpdate);

TensorAddUpdate::TensorAddUpdate()
    : _tensor()
{
}

TensorAddUpdate::TensorAddUpdate(const TensorAddUpdate &rhs)
    : _tensor(rhs._tensor->clone())
{
}

TensorAddUpdate::TensorAddUpdate(std::unique_ptr<TensorFieldValue> &&tensor)
    : _tensor(std::move(tensor))
{
}

TensorAddUpdate::~TensorAddUpdate() = default;

TensorAddUpdate &
TensorAddUpdate::operator=(const TensorAddUpdate &rhs)
{
    _tensor.reset(rhs._tensor->clone());
    return *this;
}

TensorAddUpdate &
TensorAddUpdate::operator=(TensorAddUpdate &&rhs)
{
    _tensor = std::move(rhs._tensor);
    return *this;
}

bool
TensorAddUpdate::operator==(const ValueUpdate &other) const
{
    if (other.getClass().id() != TensorAddUpdate::classId) {
        return false;
    }
    const TensorAddUpdate& o(static_cast<const TensorAddUpdate&>(other));
    if (*_tensor != *o._tensor) {
        return false;
    }
    return true;
}


void
TensorAddUpdate::checkCompatibility(const Field& field) const
{
    if (field.getDataType() != *DataType::TENSOR) {
        throw IllegalArgumentException(make_string(
                "Can not perform tensor add update on non-tensor field '%s'.",
                field.getName().data()), VESPA_STRLOC);
    }
}

std::unique_ptr<Tensor>
TensorAddUpdate::applyTo(const Tensor &tensor) const
{
    return tensor.clone();
}

bool
TensorAddUpdate::applyTo(FieldValue& value) const
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
                "Unable to perform a tensor add update on a \"%s\" field "
                "value.", value.getClass().name());
        throw IllegalStateException(err, VESPA_STRLOC);
    }
    return true;
}

void
TensorAddUpdate::printXml(XmlOutputStream& xos) const
{
    xos << "{TensorAddUpdate::printXml not yet implemented}";
}

void
TensorAddUpdate::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    (void) verbose;
    (void) indent;
    out << "{TensorAddUpdate::print not yet implemented}";
}

void
TensorAddUpdate::deserialize(const DocumentTypeRepo &repo, const DataType &type, nbostream & stream)
{
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

TensorAddUpdate*
TensorAddUpdate::clone() const
{
    return new TensorAddUpdate(*this);
}

}
