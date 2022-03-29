// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

/**
 * @class document::select::Value
 * @ingroup select
 *
 * @brief Values are entities that can be compared.
 *
 * To be able to cope with field specifications that can end up in values of
 * multiple types we need an abstraction.
 *
 * @author H�kon Humberset
 * @date 2007-20-05
 * @version $Id$
 */

#pragma once

#include <memory>
#include <map>
#include <string>
#include <vector>
#include <iosfwd>
#include "resultlist.h"

namespace document::select {

class Value : public document::Printable
{
public:
    typedef std::shared_ptr<Value> SP;
    typedef std::unique_ptr<Value> UP;
    enum Type { Invalid, Null, String, Integer, Float, Array, Struct, Bucket };

    Value(Type t) : _type(t) {}
    virtual ~Value() = default;

    Type getType() const { return _type; }

    virtual ResultList operator<(const Value& value) const = 0;
    virtual ResultList operator==(const Value& value) const = 0;

    virtual ResultList operator!=(const Value& value) const {
        return !(this->operator==(value));
    }
    virtual ResultList operator>(const Value& value) const {
        return (!(this->operator<(value)) && !(this->operator==(value)));
    }
    virtual ResultList operator>=(const Value& value) const {
        return !(this->operator<(value));
    }
    virtual ResultList operator<=(const Value& value) const {
        return ((this->operator<(value)) || (this->operator==(value)));
    }

    virtual ResultList globCompare(const Value& value) const;
    virtual ResultList regexCompare(const Value& value) const;
    virtual ResultList globTrace(const Value& value, std::ostream& trace) const;
    virtual ResultList regexTrace(const Value& value, std::ostream& trace) const;
private:
    Type _type;
};

class InvalidValue : public Value
{
public:
    InvalidValue() : Value(Invalid) {}

    ResultList operator<(const Value&) const override;
    ResultList operator==(const Value&) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

class NullValue : public Value
{
public:
    NullValue() : Value(Null) {}

    ResultList operator<(const Value&) const override;
    ResultList operator==(const Value&) const override;
    ResultList operator>(const Value &) const override;
    ResultList operator>=(const Value &) const override;
    ResultList operator<=(const Value &) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

class StringValue : public Value
{
    vespalib::string _value;

public:
    StringValue(vespalib::stringref val);

    const vespalib::string& getValue() const { return _value; }
    ResultList operator<(const Value& value) const override;
    ResultList operator==(const Value& value) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

class IntegerValue;
class FloatValue;

class NumberValue : public Value
{
public:
    typedef double CommonValueType;

    NumberValue(Type t) : Value(t) {}

    virtual CommonValueType getCommonValue() const = 0;

    virtual ResultList operator<(const Value& value) const override = 0;
    virtual ResultList operator>(const IntegerValue& value) const = 0;
    virtual ResultList operator>(const FloatValue& value) const = 0;
    virtual ResultList operator==(const Value& value) const override = 0;
    virtual ResultList operator==(const IntegerValue& value) const = 0;
    virtual ResultList operator==(const FloatValue& value) const = 0;
};

class IntegerValue : public NumberValue
{
public:
    typedef int64_t ValueType;

    IntegerValue(ValueType value, bool isBucketValue);

    ValueType getValue() const { return _value; }
    CommonValueType getCommonValue() const override { return _value; }

    ResultList operator<(const Value& value) const override;
    ResultList operator==(const Value& value) const override;

    ResultList operator>(const IntegerValue& value) const override;
    ResultList operator>(const FloatValue& value) const override;
    ResultList operator==(const IntegerValue& value) const override;
    ResultList operator==(const FloatValue& value) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
private:
    ValueType _value;
};

class FloatValue : public NumberValue
{
public:
    typedef double ValueType;

    FloatValue(ValueType val);

    ValueType getValue() const { return _value; }
    CommonValueType getCommonValue() const override { return _value; }

    ResultList operator<(const Value& value) const override;
    ResultList operator==(const Value& value) const override;

    ResultList operator>(const IntegerValue& value) const override;
    ResultList operator>(const FloatValue& value) const override;
    ResultList operator==(const IntegerValue& value) const override;
    ResultList operator==(const FloatValue& value) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
private:
    ValueType _value;
};

inline ResultList IntegerValue::operator>(const IntegerValue& value) const {
    return ResultList(Result::get(_value > value.getValue()));
}
inline ResultList IntegerValue::operator>(const FloatValue& value) const {
    return ResultList(Result::get(_value > value.getValue()));
}
inline ResultList IntegerValue::operator==(const IntegerValue& value) const {
    return ResultList(Result::get(_value == value.getValue()));
}
inline ResultList IntegerValue::operator==(const FloatValue& value) const {
    return ResultList(Result::get(_value == value.getValue()));
}

inline ResultList FloatValue::operator>(const IntegerValue& value) const {
    return ResultList(Result::get(_value > value.getValue()));
}
inline ResultList FloatValue::operator>(const FloatValue& value) const {
    return ResultList(Result::get(_value > value.getValue()));
}
inline ResultList FloatValue::operator==(const IntegerValue& value) const {
    return ResultList(Result::get(_value == value.getValue()));
}
inline ResultList FloatValue::operator==(const FloatValue& value) const {
    return ResultList(Result::get(_value == value.getValue()));
}

class ArrayValue : public Value
{
public:
    using VariableValue = std::pair<fieldvalue::VariableMap, Value::SP>;

    ArrayValue(std::vector<VariableValue> values);
    ArrayValue(const ArrayValue &) = delete;
    ArrayValue & operator =(const ArrayValue &) = delete;
    ~ArrayValue() override;

    ResultList operator<(const Value& value) const override;
    ResultList operator>(const Value& value) const override;
    ResultList operator==(const Value& value) const override;
    ResultList operator!=(const Value& value) const override;
    ResultList operator>=(const Value& value) const override;
    ResultList operator<=(const Value& value) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    ResultList globCompare(const Value& value) const override;
    ResultList regexCompare(const Value& value) const override;
    ResultList globTrace(const Value& value, std::ostream& trace) const override;
    ResultList regexTrace(const Value& value, std::ostream& trace) const override;

    template <typename Predicate>
    ResultList doCompare(const Value& value, const Predicate& cmp) const;
private:
    struct EqualsComparator;
    struct NotEqualsComparator;
    struct LessThanComparator;
    struct GreaterThanComparator;
    struct LessThanOrEqualsComparator;
    struct GreaterThanOrEqualsComparator;
    struct GlobComparator;
    struct RegexComparator;

    std::vector<VariableValue> _values;
};

class StructValue : public Value
{
public:
    using ValueMap = std::map<vespalib::string, Value::SP>;
    StructValue(ValueMap values);
    StructValue(const StructValue &) = delete;
    StructValue & operator = (const StructValue &) = delete;
    ~StructValue() override;

    ResultList operator<(const Value& value) const override;
    ResultList operator==(const Value& value) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
private:
    ValueMap _values;
};

}
