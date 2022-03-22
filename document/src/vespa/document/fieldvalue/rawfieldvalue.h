// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::RawFieldValue
 * \ingroup fieldvalue
 *
 * \brief Wrapper for raw field values.
 */
#pragma once

#include "literalfieldvalue.h"
#include <memory>

namespace document {

class RawFieldValue final : public LiteralFieldValue<RawFieldValue, DataType::T_RAW>
{
public:
    typedef LiteralFieldValue<RawFieldValue, DataType::T_RAW> Parent;

    RawFieldValue()
        : Parent(Type::RAW) { }

    RawFieldValue(const string& value)
        : Parent(Type::RAW, value) {}

    RawFieldValue(const char* rawVal, int len)
        : Parent(Type::RAW, string(rawVal, len))
    {
    }

    void accept(FieldValueVisitor &visitor) override { visitor.visit(*this); }
    void accept(ConstFieldValueVisitor &visitor) const override { visitor.visit(*this); }

    RawFieldValue* clone() const override { return new RawFieldValue(*this); }
    void printXml(XmlOutputStream& out) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    RawFieldValue& operator=(const string& value) { setValue(value); return *this; }
};

} // document

