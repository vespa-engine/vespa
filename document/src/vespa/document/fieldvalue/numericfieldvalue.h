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
    DECLARE_IDENTIFIABLE_ABSTRACT(NumericFieldValueBase);
    void printXml(XmlOutputStream& out) const override;
};

template<typename Number>
class NumericFieldValue : public NumericFieldValueBase {
protected:
    Number _value;
    bool _altered;

public:
    typedef Number value_type;

    NumericFieldValue(Number value=0) : NumericFieldValueBase(), _value(value), _altered(false) { }

    value_type getValue() const { return _value; }
    void setValue(Number newValue) { _value = newValue; }

    FieldValue& assign(const FieldValue&) override ;
    int compare(const FieldValue& other) const override;
    int fastCompare(const FieldValue& other) const override final;

    FieldValue& operator=(vespalib::stringref) override;
    FieldValue& operator=(int32_t) override;
    FieldValue& operator=(int64_t) override;
    FieldValue& operator=(float) override;
    FieldValue& operator=(double) override;
    size_t hash() const override final { return vespalib::hash<Number>()(_value); }

    char getAsByte() const override;
    int32_t getAsInt() const override;
    int64_t getAsLong() const override;
    float getAsFloat() const override;
    double getAsDouble() const override;
    vespalib::string getAsString() const override;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    bool hasChanged() const override final { return _altered; }
};

extern template class NumericFieldValue<float>;
extern template class NumericFieldValue<double>;
extern template class NumericFieldValue<int8_t>;
extern template class NumericFieldValue<int16_t>;
extern template class NumericFieldValue<int32_t>;
extern template class NumericFieldValue<int64_t>;

} // document

