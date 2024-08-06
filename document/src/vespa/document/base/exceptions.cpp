// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "exceptions.h"
#include <vespa/document/datatype/datatype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string_short::fmt;
using vespalib::IllegalStateException;

namespace document {

VESPA_IMPLEMENT_EXCEPTION_SPINE(InvalidDataTypeException);
VESPA_IMPLEMENT_EXCEPTION_SPINE(InvalidDataTypeConversionException);
VESPA_IMPLEMENT_EXCEPTION_SPINE(DocumentTypeNotFoundException);
VESPA_IMPLEMENT_EXCEPTION_SPINE(DataTypeNotFoundException);
VESPA_IMPLEMENT_EXCEPTION_SPINE(AnnotationTypeNotFoundException);
VESPA_IMPLEMENT_EXCEPTION_SPINE(FieldNotFoundException);

InvalidDataTypeException::InvalidDataTypeException(
        const DataType& actual,
        const DataType& expected,
        const vespalib::string& location)
    : IllegalStateException(fmt("Got %s while expecting %s. These types are not compatible.",
                                actual.toString().c_str(), expected.toString().c_str()),
                            location, 1),
      _actual(actual),
      _expected(expected)
{ }

InvalidDataTypeException::~InvalidDataTypeException() = default;

InvalidDataTypeConversionException::InvalidDataTypeConversionException(
        const DataType &actual,
        const DataType &expected,
        const vespalib::string& location)
    : IllegalStateException(fmt("%s can not be converted to %s.",
                                actual.toString().c_str(), expected.toString().c_str()),
                            location, 1),
      _actual(actual),
      _expected(expected)
{ }

DocumentTypeNotFoundException::DocumentTypeNotFoundException(vespalib::string name, const vespalib::string& location)
    : Exception("Document type "+name+" not found", location, 1),
      _type(std::move(name))
{ }

DataTypeNotFoundException::DataTypeNotFoundException(int id, const vespalib::string& location)
    : Exception(fmt("Data type with id %d not found", id), location, 1)
{ }

DataTypeNotFoundException::DataTypeNotFoundException(const vespalib::string& name, const vespalib::string& location)
    : Exception("Data type with name "+name+" not found.", location, 1)
{ }

DataTypeNotFoundException::~DataTypeNotFoundException() = default;

AnnotationTypeNotFoundException::AnnotationTypeNotFoundException(int id, const vespalib::string& location)
    : Exception(fmt("Data type with id %d not found", id), location, 1)
{ }

FieldNotFoundException::
FieldNotFoundException(vespalib::string fieldName, const vespalib::string& location)
    : Exception("Field with name " + fieldName + " not found", location, 1),
      _fieldName(std::move(fieldName)),
      _fieldId(0)
{ }

FieldNotFoundException::
FieldNotFoundException(int fieldId, int16_t serializationVersion, const vespalib::string& location)
    : Exception((serializationVersion < Document::getNewestSerializationVersion())
                ? fmt("Field with id %i (serialization version %d) not found", fieldId, serializationVersion)
                : fmt("Field with id %i not found", fieldId),
                location, 1),
      _fieldName(),
      _fieldId(fieldId)
{ }

InvalidDataTypeConversionException::~InvalidDataTypeConversionException() = default;
AnnotationTypeNotFoundException::~AnnotationTypeNotFoundException() = default;
FieldNotFoundException::FieldNotFoundException(const FieldNotFoundException &) = default;
FieldNotFoundException & FieldNotFoundException::operator = (const FieldNotFoundException &) = default;
FieldNotFoundException::~FieldNotFoundException() = default;
DocumentTypeNotFoundException::DocumentTypeNotFoundException(const DocumentTypeNotFoundException &) = default;
DocumentTypeNotFoundException & DocumentTypeNotFoundException::operator = (const DocumentTypeNotFoundException &) = default;
DocumentTypeNotFoundException::~DocumentTypeNotFoundException() = default;

VESPA_IMPLEMENT_EXCEPTION(WrongTensorTypeException, vespalib::Exception);

}
