// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "literalfieldvalue.h"
#include "literalfieldvalue.hpp"
#include <vespa/document/util/stringutil.h>
#include <vespa/vespalib/util/xmlstream.h>
#include <sstream>

using namespace vespalib::xml;

namespace document {

IMPLEMENT_IDENTIFIABLE_ABSTRACT(LiteralFieldValueB, FieldValue);

LiteralFieldValueB::LiteralFieldValueB() :
    FieldValue(),
    _value(),
    _backing(),
    _altered(true)
{
    _value = _backing;
}

LiteralFieldValueB::~LiteralFieldValueB() { }

LiteralFieldValueB::LiteralFieldValueB(const LiteralFieldValueB& other)
    : FieldValue(other),
      _value(),
      _backing(other.getValueRef()),
      _altered(other._altered)
{
    _value = _backing;
}

LiteralFieldValueB::LiteralFieldValueB(const string& value)
    : FieldValue(),
      _value(),
      _backing(value),
      _altered(true)
{
    _value = _backing;
}

LiteralFieldValueB &
LiteralFieldValueB::operator=(const LiteralFieldValueB& other)
{
    FieldValue::operator=(other);
    _backing = other.getValueRef();
    _value = _backing;
    _altered = other._altered;
    return *this;
}

FieldValue&
LiteralFieldValueB::assign(const FieldValue& value)
{
    if (value.getDataType() == getDataType()) {
        return operator=(static_cast<const LiteralFieldValueB&>(value));
    }
    return FieldValue::assign(value);
}

int
LiteralFieldValueB::compare(const FieldValue& other) const
{
    if (*getDataType() == *other.getDataType()) {
        const LiteralFieldValueB& sval(static_cast<const LiteralFieldValueB&>(other));
        return getValueRef().compare(sval.getValueRef());
    }
    return (getDataType()->getId() - other.getDataType()->getId());
}

int
LiteralFieldValueB::fastCompare(const FieldValue& other) const
{
    const LiteralFieldValueB& sval(static_cast<const LiteralFieldValueB&>(other));
    return getValueRef().compare(sval.getValueRef());
}

void
LiteralFieldValueB::printXml(XmlOutputStream& out) const
{
    out << XmlContentWrapper(_value.data(), _value.size());
}

void
LiteralFieldValueB::
print(std::ostream& out, bool, const std::string&) const
{
    vespalib::string escaped;
    out << StringUtil::escape(getValue(), escaped);
}

FieldValue&
LiteralFieldValueB::operator=(vespalib::stringref value)
{
    setValue(value);
    return *this;
}

vespalib::string
LiteralFieldValueB::getAsString() const
{
    return getValue();
}

std::pair<const char*, size_t>
LiteralFieldValueB::getAsRaw() const
{
    return std::make_pair(_value.data(), _value.size());
}

void
LiteralFieldValueB::syncBacking() const
{
    _backing = _value;
    _value = _backing;
}


namespace {
template <typename T>
std::string valueToString(T value) {
    std::ostringstream ost;
    ost << value;
    return ost.str();
}
}  // namespace

FieldValue&
LiteralFieldValueB::operator=(int32_t value)
{
    setValue(valueToString(value));
    return *this;
}

FieldValue&
LiteralFieldValueB::operator=(int64_t value)
{
    setValue(valueToString(value));
    return *this;
}

FieldValue&
LiteralFieldValueB::operator=(float value)
{
    setValue(valueToString(value));
    return *this;
}

FieldValue&
LiteralFieldValueB::operator=(double value)
{
    setValue(valueToString(value));
    return *this;
}

template class LiteralFieldValue<RawFieldValue, DataType::T_RAW, false>;
template class LiteralFieldValue<StringFieldValue, DataType::T_STRING, true>;

}  // namespace document
