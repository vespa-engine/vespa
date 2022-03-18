// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fieldvalue.h"

namespace document {

/**
 * Represent the value in a field of type 'bool' which can be either true or false.
 **/
class BoolFieldValue final : public FieldValue {
    bool _value;

public:
    BoolFieldValue(bool value=false);
    ~BoolFieldValue() override;

    void accept(FieldValueVisitor &visitor) override { visitor.visit(*this); }
    void accept(ConstFieldValueVisitor &visitor) const override { visitor.visit(*this); }

    FieldValue *clone() const override;
    int compare(const FieldValue &rhs) const override;

    void printXml(XmlOutputStream &out) const override;
    void print(std::ostream &out, bool verbose, const std::string &indent) const override;

    const DataType *getDataType() const override;

    bool getValue() const { return _value; }
    void setValue(bool v) { _value = v; }

    FieldValue &assign(const FieldValue &rhs) override;

    char getAsByte() const override;
    int32_t getAsInt() const override;
    int64_t getAsLong() const override;
    float getAsFloat() const override;
    double getAsDouble() const override;
    vespalib::string getAsString() const override;

    BoolFieldValue& operator=(vespalib::stringref) override;
    static std::unique_ptr<BoolFieldValue> make(bool value=false) { return std::make_unique<BoolFieldValue>(value); }
};

}
