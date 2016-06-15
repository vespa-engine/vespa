// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::RawFieldValue
 * \ingroup fieldvalue
 *
 * \brief Wrapper for raw field values.
 */
#pragma once

#include <vespa/document/fieldvalue/literalfieldvalue.h>
#include <memory>

namespace document {

class RawFieldValue
        : public LiteralFieldValue<RawFieldValue, DataType::T_RAW, false>
{
public:
    typedef LiteralFieldValue<RawFieldValue, DataType::T_RAW, false> Parent;

    RawFieldValue()
        : Parent() { }

    RawFieldValue(const string& value)
        : Parent(value) {}

    RawFieldValue(const char* rawVal, int len)
        : Parent(string(rawVal, len))
    {
    }

    void accept(FieldValueVisitor &visitor) override { visitor.visit(*this); }
    void accept(ConstFieldValueVisitor &visitor) const override { visitor.visit(*this); }

    virtual RawFieldValue* clone() const
        { return new RawFieldValue(*this); }
    virtual void printXml(XmlOutputStream& out) const;
    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;

    RawFieldValue& operator=(const string& value)
        { setValue(value); return *this; }

    DECLARE_IDENTIFIABLE(RawFieldValue);
};

} // document

