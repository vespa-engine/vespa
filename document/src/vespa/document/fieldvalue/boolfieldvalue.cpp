// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "boolfieldvalue.h"
#include <vespa/document/datatype/datatype.h>
#include <vespa/vespalib/util/xmlstream.h>
#include <ostream>

using namespace vespalib::xml;

namespace document {

BoolFieldValue::BoolFieldValue(bool value)
    : FieldValue(Type::BOOL), _value(value) {
}

BoolFieldValue::~BoolFieldValue() = default;

FieldValue &
BoolFieldValue::assign(const FieldValue &rhs) {
    if (rhs.isA(Type::BOOL)) {
        operator=(static_cast<const BoolFieldValue &>(rhs));
        return *this;
    } else {
        return FieldValue::assign(rhs);
    }
}

int
BoolFieldValue::compare(const FieldValue&rhs) const {
    int diff = FieldValue::compare(rhs);
    if (diff != 0) return diff;
    const BoolFieldValue &o = static_cast<const BoolFieldValue &>(rhs);
    return (_value == o._value) ? 0 : _value ? 1 : -1;
}

void
BoolFieldValue::printXml(XmlOutputStream& out) const {
    out << XmlContent(getAsString());
}

void
BoolFieldValue::print(std::ostream& out, bool, const std::string&) const {
    out << (_value ? "true" : "false") << "\n";
}

const DataType *
BoolFieldValue::getDataType() const {
    return DataType::BOOL;
}

FieldValue *
BoolFieldValue::clone() const {
    return new BoolFieldValue(*this);
}

char
BoolFieldValue::getAsByte() const {
    return _value ? 1 : 0;
}
int32_t
BoolFieldValue::getAsInt() const {
    return _value ? 1 : 0;
}
int64_t
BoolFieldValue::getAsLong() const {
    return _value ? 1 : 0;
}
float
BoolFieldValue::getAsFloat() const {
    return _value ? 1 : 0;
}
double
BoolFieldValue::getAsDouble() const {
    return _value ? 1 : 0;
}
vespalib::string
BoolFieldValue::getAsString() const {
    return _value ? "true" : "false";
}

BoolFieldValue&
BoolFieldValue::operator=(vespalib::stringref v) {
    _value = (v == "true");
    return *this;
}

}  // namespace document
