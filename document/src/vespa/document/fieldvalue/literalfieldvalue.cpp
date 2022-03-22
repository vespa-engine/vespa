// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "literalfieldvalue.hpp"
#include <vespa/document/util/stringutil.h>
#include <vespa/vespalib/util/xmlstream.h>

using namespace vespalib::xml;

namespace document {

LiteralFieldValueB::LiteralFieldValueB(Type type) :
    FieldValue(type),
    _value(),
    _backing()
{
    _value = _backing;
}

LiteralFieldValueB::~LiteralFieldValueB() = default;

LiteralFieldValueB::LiteralFieldValueB(const LiteralFieldValueB& other)
    : FieldValue(other),
      _value(),
      _backing(other.getValueRef())
{
    _value = _backing;
}

LiteralFieldValueB::LiteralFieldValueB(Type type, const stringref & value)
    : FieldValue(type),
      _value(),
      _backing(value)
{
    _value = _backing;
}

LiteralFieldValueB &
LiteralFieldValueB::operator=(const LiteralFieldValueB& other)
{
    FieldValue::operator=(other);
    _backing = other.getValueRef();
    _value = _backing;
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

template class LiteralFieldValue<RawFieldValue, DataType::T_RAW>;
template class LiteralFieldValue<StringFieldValue, DataType::T_STRING>;

}  // namespace document
