// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "arithmeticvalueupdate.h"
#include <vespa/document/base/field.h>
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/xmlstream.h>

using vespalib::IllegalArgumentException;
using vespalib::IllegalStateException;
using namespace vespalib::xml;

namespace document {

IMPLEMENT_IDENTIFIABLE(ArithmeticValueUpdate, ValueUpdate);

// Declare string representations for operator names.
static const char * operatorName[]  = { "add", "div", "mul", "sub" };
static const char * operatorNameC[] = { "Add", "Div", "Mul", "Sub" };

bool
ArithmeticValueUpdate::operator==(const ValueUpdate& other) const
{
    if (other.getClass().id() != ArithmeticValueUpdate::classId) return false;
    const ArithmeticValueUpdate& o(static_cast<const ArithmeticValueUpdate&>(other));
    if (_operator != o._operator) return false;
    if (_operand != o._operand) return false;
    return true;
}

// Ensure that this update is compatible with given field.
void
ArithmeticValueUpdate::checkCompatibility(const Field& field) const
{
    if ( ! field.getDataType().inherits(NumericDataType::classId)) {
        throw IllegalArgumentException(vespalib::make_string(
                "Can not perform arithmetic update on non-numeric field '%s'.",
                field.getName().c_str()), VESPA_STRLOC);	
    }
}

// Apply this update.
bool
ArithmeticValueUpdate::applyTo(FieldValue& value) const
{
    if (value.inherits(ByteFieldValue::classId)) {
        ByteFieldValue& bValue = static_cast<ByteFieldValue&>(value);
        bValue.setValue((int)applyTo(static_cast<int64_t>(bValue.getAsInt())));
    } else if (value.inherits(DoubleFieldValue::classId)) {
        DoubleFieldValue& dValue = static_cast<DoubleFieldValue&>(value);
        dValue.setValue(applyTo(dValue.getAsDouble()));
    } else if (value.inherits(FloatFieldValue::classId)) {
        FloatFieldValue& fValue = static_cast<FloatFieldValue&>(value);
        fValue.setValue((float)applyTo(fValue.getAsFloat()));
    } else if (value.inherits(IntFieldValue::classId)) {
        IntFieldValue& iValue = static_cast<IntFieldValue&>(value);
        iValue.setValue((int)applyTo(static_cast<int64_t>(iValue.getAsInt())));
    } else if (value.inherits(LongFieldValue::classId)) {
        LongFieldValue& lValue = static_cast<LongFieldValue&>(value);
        lValue.setValue(applyTo(lValue.getAsLong()));
    } else {
        std::string err = vespalib::make_string(
                "Unable to perform an arithmetic update on a \"%s\" field "
                "value.", value.getClass().name());
        throw IllegalStateException(err, VESPA_STRLOC);
    }
    return true;
}

// Perform the contained operation on the given value.
double
ArithmeticValueUpdate::applyTo(double value) const
{
    switch(_operator) {
    case Add:
        return value + _operand;
    case Div:
        return value / _operand;
    case Mul:
        return value * _operand;
    case Sub:
        return value - _operand;
    default:
        return 0;
    }
}

// Perform the contained operation on the given value.
long
ArithmeticValueUpdate::applyTo(int64_t value) const
{
    switch(_operator) {
    case Add:
        return (long)(value + _operand);
    case Div:
        return (long)(value / _operand);
    case Mul:
        return (long)(value * _operand);
    case Sub:
        return (long)(value - _operand);
    default:
        return 0;
    }
}

// Perform the contained operation on the given value.
std::string
ArithmeticValueUpdate::applyTo(const std::string & value) const
{
    return value;
}

// Print this update as a human readable string.
void
ArithmeticValueUpdate::print(std::ostream& out, bool, const std::string& indent) const
{
    out << indent << "ArithmeticValueUpdate(" << operatorNameC[_operator] << " " << _operand << ")";
}

void
ArithmeticValueUpdate::printXml(XmlOutputStream& xos) const
{
    xos << XmlTag(operatorName[_operator])
        << XmlAttribute("by", _operand)
        << XmlEndTag();
}

// Deserialize this update from the given buffer.
void
ArithmeticValueUpdate::deserialize(const DocumentTypeRepo&, const DataType&, nbostream & stream)
{
    int32_t opt;
    stream >> opt >>_operand;
    _operator = static_cast<ArithmeticValueUpdate::Operator>(opt);
}

} // document
