// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_remove_update.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/xmlstream.h>
#include <ostream>

using vespalib::IllegalArgumentException;
using vespalib::IllegalStateException;
using vespalib::tensor::Tensor;
using vespalib::make_string;

namespace document {

IMPLEMENT_IDENTIFIABLE(TensorRemoveUpdate, ValueUpdate);

TensorRemoveUpdate::TensorRemoveUpdate()
    : _tensor()
{
}

TensorRemoveUpdate::TensorRemoveUpdate(const TensorRemoveUpdate &rhs)
    : _tensor(rhs._tensor->clone())
{
}

TensorRemoveUpdate::TensorRemoveUpdate(std::unique_ptr<TensorFieldValue> &&tensor)
    : _tensor(std::move(tensor))
{
}

TensorRemoveUpdate::~TensorRemoveUpdate() = default;

TensorRemoveUpdate &
TensorRemoveUpdate::operator=(const TensorRemoveUpdate &rhs)
{
    _tensor.reset(rhs._tensor->clone());
    return *this;
}

TensorRemoveUpdate &
TensorRemoveUpdate::operator=(TensorRemoveUpdate &&rhs)
{
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
        throw IllegalArgumentException(make_string(
                "Can not perform tensor remove update on non-tensor field '%s'.",
                field.getName().data()), VESPA_STRLOC);
    }
}

std::unique_ptr<Tensor>
TensorRemoveUpdate::applyTo(const Tensor &tensor) const
{
    // TODO: implement
    (void) tensor;
    return std::unique_ptr<Tensor>();
}

bool
TensorRemoveUpdate::applyTo(FieldValue &value) const
{
    // TODO: implement
    (void) value;
    return false;
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

void
TensorRemoveUpdate::deserialize(const DocumentTypeRepo &repo, const DataType &type, nbostream &stream)
{
    auto tensor = type.createFieldValue();
    if (tensor->inherits(TensorFieldValue::classId)) {
        _tensor.reset(static_cast<TensorFieldValue *>(tensor.release()));
    } else {
        std::string err = make_string(
                "Expected tensor field value, got a \"%s\" field value.", tensor->getClass().name());
        throw IllegalStateException(err, VESPA_STRLOC);
    }
    VespaDocumentDeserializer deserializer(repo, stream, Document::getNewestSerializationVersion());
    deserializer.read(*_tensor);
}

TensorRemoveUpdate *
TensorRemoveUpdate::clone() const
{
    return new TensorRemoveUpdate(*this);
}

}
