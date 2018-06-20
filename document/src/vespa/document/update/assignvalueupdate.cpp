// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "assignvalueupdate.h"
#include <vespa/document/base/field.h>
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/xmlstream.h>

using vespalib::IllegalArgumentException;
using vespalib::IllegalStateException;
using vespalib::nbostream;
using namespace vespalib::xml;

namespace document {

IMPLEMENT_IDENTIFIABLE(AssignValueUpdate, ValueUpdate);

AssignValueUpdate::AssignValueUpdate() = default;

AssignValueUpdate::AssignValueUpdate(const FieldValue& value)
    : ValueUpdate(),
      _value(value.clone())
{
}
AssignValueUpdate::~AssignValueUpdate() = default;

// Declare content bits.
static const unsigned char CONTENT_HASVALUE = 0x01;

bool
AssignValueUpdate::operator==(const ValueUpdate& other) const
{
    if (other.getClass().id() != AssignValueUpdate::classId) return false;
    const AssignValueUpdate& o(static_cast<const AssignValueUpdate&>(other));
    return _value == o._value;
}

// Ensure that this update is compatible with given field.
void
AssignValueUpdate::checkCompatibility(const Field& field) const
{
        // If equal datatype we know it is ok.
    if (!_value || field.getDataType().isValueType(*_value)) {
        return;
    }
        // Deny all assignments to non-equal types
    throw IllegalArgumentException(vespalib::make_string(
            "Failed to assign field value of type %s to value of type %s.",
            _value->getDataType()->toString().c_str(),
            field.getDataType().toString().c_str()), VESPA_STRLOC);
}

// Print this update as a human readable string.
void
AssignValueUpdate::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    out << indent << "AssignValueUpdate(";
    if (_value) _value->print(out, verbose, indent);
    out << ")";
}

// Apply this update to the given document.
bool
AssignValueUpdate::applyTo(FieldValue& value) const
{
    if (_value && (_value->getDataType() != value.getDataType())) {
        vespalib::string err = vespalib::make_string(
                "Unable to assign a \"%s\" value to a \"%s\" field value.",
                _value->getClass().name(), value.getClass().name());
        throw IllegalStateException(err, VESPA_STRLOC);	
    }
    if (_value) {
        value.assign(*_value);
    }
    return bool(_value);
}

void
AssignValueUpdate::printXml(XmlOutputStream& xos) const
{
    xos << XmlTag("assign");
    if (_value) {
        _value->printXml(xos);
    }
    xos << XmlEndTag();
}

// Deserialize this update from the given buffer.
void
AssignValueUpdate::deserialize(const DocumentTypeRepo& repo, const DataType& type, nbostream & stream)
{
    // Read content bit vector.
    uint8_t content = 0x00;
    stream >> content;

    // Read field value, if any.
    if (content & CONTENT_HASVALUE) {
        _value.reset(type.createFieldValue().release());
        VespaDocumentDeserializer deserializer(repo, stream, Document::getNewestSerializationVersion());
        deserializer.read(*_value);
    }
}

}  // namespace document
