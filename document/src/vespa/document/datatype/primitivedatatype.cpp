// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "primitivedatatype.h"
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".document.datatype.primitivedatatype");

namespace document {

IMPLEMENT_IDENTIFIABLE_ABSTRACT(PrimitiveDataType, DataType);

namespace {
    const char *Int = "Int";
    const char *Short = "Short";
    const char *Float = "Float";
    const char *String = "String";
    const char *Raw = "Raw";
    const char *Long = "Long";
    const char *Double = "Double";
    const char *Uri = "Uri";
    const char *Byte = "Byte";
    const char *Predicate = "Predicate";
    const char *Tensor = "Tensor";

    const vespalib::stringref getTypeName(DataType::Type type) {
        switch (type) {
            case DataType::T_INT: return Int;
            case DataType::T_SHORT: return Short;
            case DataType::T_FLOAT: return Float;
            case DataType::T_STRING: return String;
            case DataType::T_RAW: return Raw;
            case DataType::T_LONG: return Long;
            case DataType::T_DOUBLE: return Double;
            case DataType::T_URI: return Uri;
            case DataType::T_BYTE: return Byte;
            case DataType::T_PREDICATE: return Predicate;
            case DataType::T_TENSOR: return Tensor;
            default:
                throw vespalib::IllegalArgumentException(vespalib::make_string(
                        "Type %i is not a primitive type", type), VESPA_STRLOC);
        }
    }

}

PrimitiveDataType::PrimitiveDataType(Type type)
    : DataType(getTypeName(type), type)
{
}

FieldValue::UP
PrimitiveDataType::createFieldValue() const
{
    switch (getId()) {
        case T_INT: return FieldValue::UP(new IntFieldValue);
        case T_SHORT: return FieldValue::UP(new ShortFieldValue);
        case T_FLOAT: return FieldValue::UP(new FloatFieldValue);
        case T_URI: return FieldValue::UP(new StringFieldValue);
        case T_STRING: return FieldValue::UP(new StringFieldValue);
        case T_RAW: return FieldValue::UP(new RawFieldValue);
        case T_LONG: return FieldValue::UP(new LongFieldValue);
        case T_DOUBLE: return FieldValue::UP(new DoubleFieldValue);
        case T_BYTE: return FieldValue::UP(new ByteFieldValue);
        case T_PREDICATE: return FieldValue::UP(new PredicateFieldValue);
        case T_TENSOR: return std::make_unique<TensorFieldValue>();
    }
    LOG_ABORT("getId() returned value out of range");
}

void
PrimitiveDataType::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "PrimitiveDataType(" << getName() << ", id " << getId() << ")";
}

void
PrimitiveDataType::onBuildFieldPath(FieldPath &, const vespalib::stringref & rest) const
{
    if ( ! rest.empty()) {
        std::ostringstream ost;
        ost << "Datatype " << *this << " does not support further recursive structure: " << rest;
        throw vespalib::IllegalArgumentException(ost.str());
    }
}


} // document
