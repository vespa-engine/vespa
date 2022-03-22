// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::NumericFieldValue
 * \ingroup fieldvalue
 *
 * \brief Templated parent class for field values of numbers.
 *
 * To prevent code duplication, numeric values are implemented using this
 * template.
 */
#pragma once

#include "fieldvalue.h"
#include <vespa/vespalib/stllike/hash_fun.h>

namespace document {

class NumericFieldValueBase : public FieldValue
{
public:
    void printXml(XmlOutputStream& out) const override;
protected:
    NumericFieldValueBase(Type type) : FieldValue(type) {}
};

template<typename Number>
class NumericFieldValue : public NumericFieldValueBase {
protected:
    explicit NumericFieldValue(Type type, Number value=0) : NumericFieldValueBase(type), _value(value) { }
    Number _value;
public:
    typedef Number value_type;

    value_type getValue() const { return _value; }
    void setValue(Number newValue) { _value = newValue; }

    FieldValue& assign(const FieldValue&) override ;
    int compare(const FieldValue& other) const override;
    int fastCompare(const FieldValue& other) const override final;

    FieldValue& operator=(vespalib::stringref) override;
    size_t hash() const override final { return vespalib::hash<Number>()(_value); }

    char getAsByte() const override;
    int32_t getAsInt() const override;
    int64_t getAsLong() const override;
    float getAsFloat() const override;
    double getAsDouble() const override;
    vespalib::string getAsString() const override;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

extern template class NumericFieldValue<float>;
extern template class NumericFieldValue<double>;
extern template class NumericFieldValue<int8_t>;
extern template class NumericFieldValue<int16_t>;
extern template class NumericFieldValue<int32_t>;
extern template class NumericFieldValue<int64_t>;

} // document

