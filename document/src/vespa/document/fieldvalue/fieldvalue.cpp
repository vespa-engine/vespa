// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fieldvalue.h"
#include "arrayfieldvalue.h"
#include "intfieldvalue.h"
#include "floatfieldvalue.h"
#include "stringfieldvalue.h"
#include "rawfieldvalue.h"
#include "longfieldvalue.h"
#include "doublefieldvalue.h"
#include "bytefieldvalue.h"
#include "predicatefieldvalue.h"
#include "iteratorhandler.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/serialization/vespadocumentserializer.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/polymorphicarrays.h>
#include <vespa/vespalib/util/xmlstream.h>
#include <sstream>

using vespalib::nbostream;
using vespalib::IllegalArgumentException;
using namespace vespalib::xml;

namespace document {

using namespace fieldvalue;

const char *
FieldValue::className() const noexcept {
    switch (type()) {
        case Type::BOOL:
            return "BoolFieldValue";
        case Type::BYTE:
            return "ByteFieldValue";
        case Type::SHORT:
            return "ShortFieldValue";
        case Type::INT:
            return "IntFieldValue";
        case Type::LONG:
            return "LongFieldValue";
        case Type::FLOAT:
            return "FloatFieldValue";
        case Type::DOUBLE:
            return "DoubleFieldValue";
        case Type::STRING:
            return "StringFieldValue";
        case Type::RAW:
            return "RawFieldValue";
        case Type::PREDICATE:
            return "PredicateFieldValue";
        case Type::TENSOR:
            return "TensorFieldValue";
        case Type::ANNOTATION_REFERENCE:
            return "AnnotationReferenceFieldValue";
        case Type::REFERENCE:
            return "ReferenceFieldValue";
        case Type::ARRAY:
            return "ArrayFieldValue";
        case Type::WSET:
            return "WSetFieldValue";
        case Type::MAP:
            return "MapFieldValue";
        case Type::STRUCT:
            return "StructFieldValue";
        case Type::DOCUMENT:
            return "DocumentFieldValue";
        case Type::NONE:
        default:
            abort();
    }
}
void FieldValue::serialize(nbostream &stream) const {
    VespaDocumentSerializer serializer(stream);
    serializer.write(*this);
}

nbostream
FieldValue::serialize() const {
    nbostream stream;
    serialize(stream);
    return stream;
}

size_t
FieldValue::hash() const
{
    vespalib::nbostream os;
    serialize(os);
    return vespalib::hashValue(os.data(), os.size()) ;
}

int
FieldValue::compare(const FieldValue& other) const {
    return getDataType()->cmpId(*other.getDataType());
}

int
FieldValue::fastCompare(const FieldValue& other) const {
    return compare(other);
}

FieldValue&
FieldValue::assign(const FieldValue& value)
{
    throw IllegalArgumentException(
            "Cannot assign value of type " + value.getDataType()->toString()
            + " to value of type " + value.getDataType()->toString(), VESPA_STRLOC);
}

/**
 * Normally, tag names are decided by the parent node, so children will not
 * print their parents. For toXML to work on non-root (non-document) nodes
 * though, we've overwritten toXml to write a start tag for leaf nodes, if
 * they are used as root nodes in toXml call.
 */
std::string
FieldValue::toXml(const std::string& indent) const
{
    std::ostringstream ost;
    XmlOutputStream xos(ost, indent);
    xos << XmlTag("value");
    printXml(xos);
    xos << XmlEndTag();
    return ost.str();
}

// Subtypes should implement the conversion functions that make sense

FieldValue&
FieldValue::operator=(vespalib::stringref)
{
    throw IllegalArgumentException("Cannot assign string to datatype " + getDataType()->toString(), VESPA_STRLOC);
}

char
FieldValue::getAsByte() const
{
    throw InvalidDataTypeConversionException(*getDataType(), *DataType::BYTE, VESPA_STRLOC);
}

int32_t
FieldValue::getAsInt() const
{
    throw InvalidDataTypeConversionException(*getDataType(), *DataType::INT, VESPA_STRLOC);
}

int64_t
FieldValue::getAsLong() const
{
    throw InvalidDataTypeConversionException(*getDataType(), *DataType::LONG, VESPA_STRLOC);
}

float
FieldValue::getAsFloat() const
{
    throw InvalidDataTypeConversionException(*getDataType(), *DataType::FLOAT, VESPA_STRLOC);
}

double
FieldValue::getAsDouble() const
{
    throw InvalidDataTypeConversionException(*getDataType(), *DataType::DOUBLE, VESPA_STRLOC);
}

vespalib::string
FieldValue::getAsString() const
{
    throw InvalidDataTypeConversionException(*getDataType(), *DataType::STRING, VESPA_STRLOC);
}

std::pair<const char*, size_t>
FieldValue::getAsRaw() const
{
    throw InvalidDataTypeConversionException(*getDataType(), *DataType::RAW, VESPA_STRLOC);
}

FieldValue::UP
FieldValue::getNestedFieldValue(PathRange nested) const
{
    return ( ! nested.atEnd() ) ? onGetNestedFieldValue(nested) : FieldValue::UP();
}

FieldValue::UP
FieldValue::onGetNestedFieldValue(PathRange nested) const
{
    (void) nested;
    return FieldValue::UP();
}

ModificationStatus
FieldValue::iterateNested(PathRange nested, IteratorHandler & handler) const
{
    return onIterateNested(nested, handler);
}

ModificationStatus
FieldValue::onIterateNested(PathRange nested, IteratorHandler & handler) const
{
    if (nested.atEnd()) {
        handler.handlePrimitive(-1, *this);
        return handler.modify(const_cast<FieldValue&>(*this));
    } else {
        throw vespalib::IllegalArgumentException("Primitive types can't be iterated through");
    }
}

std::string
FieldValue::toString(bool verbose, const std::string& indent) const
{
    std::ostringstream o;
    print(o, verbose, indent);
    return o.str();
}

using vespalib::ComplexArrayT;
using vespalib::PrimitiveArrayT;

namespace {

class FieldValueFactory : public ComplexArrayT<FieldValue>::Factory
{
public:
    FieldValueFactory(const DataType & dataType) : _dataType(&dataType) { }
    FieldValue * create() override { return _dataType->createFieldValue().release(); }
    FieldValueFactory * clone() const override { return new FieldValueFactory(*this); }
private:
    const DataType * _dataType;
};

}

std::unique_ptr<vespalib::IArrayBase>
FieldValue::createArray(const DataType & baseType)
{
    switch(baseType.getId()) {
    case DataType::T_INT:
        return std::make_unique<PrimitiveArrayT<IntFieldValue, FieldValue>>();
    case DataType::T_FLOAT:
        return std::make_unique<PrimitiveArrayT<FloatFieldValue, FieldValue>>();
    case DataType::T_STRING:
        return std::make_unique<PrimitiveArrayT<StringFieldValue, FieldValue>>();
    case DataType::T_RAW:
        return std::make_unique<PrimitiveArrayT<RawFieldValue, FieldValue>>();
    case DataType::T_LONG:
        return std::make_unique<PrimitiveArrayT<LongFieldValue, FieldValue>>();
    case DataType::T_DOUBLE:
        return std::make_unique<PrimitiveArrayT<DoubleFieldValue, FieldValue>>();
    case DataType::T_BYTE:
        return std::make_unique<PrimitiveArrayT<ByteFieldValue, FieldValue>>();
    default:
        return std::make_unique<ComplexArrayT<FieldValue>>(std::make_unique<FieldValueFactory>(baseType));
    }
}

std::ostream& operator<<(std::ostream& out, const FieldValue & p) {
    p.print(out, false, "");
    return out;
}

XmlOutputStream & operator<<(XmlOutputStream & out, const FieldValue & p) {
    p.printXml(out);
    return out;
}

} // document
