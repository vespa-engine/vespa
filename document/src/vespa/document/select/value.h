// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

namespace document {
namespace select {

class Value : public document::Printable
{
public:
    typedef std::shared_ptr<Value> SP;
    typedef std::unique_ptr<Value> UP;
    enum Type { Invalid, Null, String, Integer, Float, Array, Struct, Bucket };

    Value(Type t) : _type(t) {}
    virtual ~Value() {}

    Type getType() const { return _type; }

    virtual ResultList operator<(const Value& value) const = 0;
    virtual ResultList operator==(const Value& value) const = 0;

    virtual UP clone() const = 0;

    virtual ResultList operator!=(const Value& value) const
        { return !(this->operator==(value)); }
    virtual ResultList operator>(const Value& value) const
        { return (!(this->operator<(value)) && !(this->operator==(value))); }
    virtual ResultList operator>=(const Value& value) const
        { return !(this->operator<(value)); }
    virtual ResultList operator<=(const Value& value) const
        { return ((this->operator<(value)) || (this->operator==(value))); }

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

    virtual ResultList operator<(const Value&) const;
    virtual ResultList operator==(const Value&) const;
    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;

    Value::UP clone() const { return Value::UP(new InvalidValue()); }

};

class NullValue : public Value
{
public:
    NullValue() : Value(Null) {}

    virtual ResultList operator<(const Value&) const;
    virtual ResultList operator==(const Value&) const;

    virtual ResultList
    operator>(const Value &) const;

    virtual ResultList
    operator>=(const Value &) const;

    virtual ResultList
    operator<=(const Value &) const;

    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;

    Value::UP clone() const { return Value::UP(new NullValue()); }
};

class StringValue : public Value
{
    vespalib::string _value;

public:
    StringValue(const vespalib::stringref & val);

    const vespalib::string& getValue() const { return _value; }

    virtual ResultList operator<(const Value& value) const;
    virtual ResultList operator==(const Value& value) const;
    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;

    Value::UP clone() const { return Value::UP(new StringValue(_value)); }
};

class IntegerValue;
class FloatValue;

class NumberValue : public Value
{
public:
    typedef double CommonValueType;

    NumberValue(Type t) : Value(t) {}

    virtual CommonValueType getCommonValue() const = 0;

    virtual ResultList operator<(const Value& value) const = 0;
    virtual ResultList operator>(const IntegerValue& value) const = 0;
    virtual ResultList operator>(const FloatValue& value) const = 0;
    virtual ResultList operator==(const Value& value) const = 0;
    virtual ResultList operator==(const IntegerValue& value) const = 0;
    virtual ResultList operator==(const FloatValue& value) const = 0;
};

class IntegerValue : public NumberValue
{
public:
    typedef int64_t ValueType;

    IntegerValue(ValueType value, bool isBucketValue);

    ValueType getValue() const { return _value; }
    CommonValueType getCommonValue() const { return _value; }

    virtual ResultList operator<(const Value& value) const;
    virtual ResultList operator==(const Value& value) const;

    virtual ResultList operator>(const IntegerValue& value) const;
    virtual ResultList operator>(const FloatValue& value) const;
    virtual ResultList operator==(const IntegerValue& value) const;
    virtual ResultList operator==(const FloatValue& value) const;
    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;

    Value::UP clone() const {
        return Value::UP(new IntegerValue(_value, getType() == Value::Bucket));
    }
private:
    ValueType _value;
};

class FloatValue : public NumberValue
{
public:
    typedef double ValueType;

    FloatValue(ValueType val);

    ValueType getValue() const { return _value; }
    CommonValueType getCommonValue() const { return _value; }

    virtual ResultList operator<(const Value& value) const;
    virtual ResultList operator==(const Value& value) const;

    virtual ResultList operator>(const IntegerValue& value) const;
    virtual ResultList operator>(const FloatValue& value) const;
    virtual ResultList operator==(const IntegerValue& value) const;
    virtual ResultList operator==(const FloatValue& value) const;
    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;

    Value::UP clone() const { return Value::UP(new FloatValue(_value)); }
private:
    ValueType _value;
};

inline ResultList IntegerValue::operator>(const IntegerValue& value) const
    { return ResultList(Result::get(_value > value.getValue())); }
inline ResultList IntegerValue::operator>(const FloatValue& value) const
    { return ResultList(Result::get(_value > value.getValue())); }
inline ResultList IntegerValue::operator==(const IntegerValue& value) const
    { return ResultList(Result::get(_value == value.getValue())); }
inline ResultList IntegerValue::operator==(const FloatValue& value) const
    { return ResultList(Result::get(_value == value.getValue())); }

inline ResultList FloatValue::operator>(const IntegerValue& value) const
    { return ResultList(Result::get(_value > value.getValue())); }
inline ResultList FloatValue::operator>(const FloatValue& value) const
    { return ResultList(Result::get(_value > value.getValue())); }
inline ResultList FloatValue::operator==(const IntegerValue& value) const
    { return ResultList(Result::get(_value == value.getValue())); }
inline ResultList FloatValue::operator==(const FloatValue& value) const
    { return ResultList(Result::get(_value == value.getValue())); }

class ArrayValue : public Value
{
public:
    typedef std::pair<FieldValue::IteratorHandler::VariableMap, Value::SP> VariableValue;

    ArrayValue(const std::vector<VariableValue>& values);

    virtual ResultList operator<(const Value& value) const;
    virtual ResultList operator>(const Value& value) const;
    virtual ResultList operator==(const Value& value) const;
    virtual ResultList operator!=(const Value& value) const;
    virtual ResultList operator>=(const Value& value) const;
    virtual ResultList operator<=(const Value& value) const;
    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;

    virtual ResultList globCompare(const Value& value) const;
    virtual ResultList regexCompare(const Value& value) const;
    virtual ResultList globTrace(const Value& value, std::ostream& trace) const;
    virtual ResultList regexTrace(const Value& value, std::ostream& trace) const;

    template <typename Predicate>
    ResultList doCompare(const Value& value, const Predicate& cmp) const;

    Value::UP clone() const { return Value::UP(new ArrayValue(_values)); }

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
    typedef std::map<vespalib::string, Value::SP> ValueMap;
    StructValue(const ValueMap & values);

    virtual ResultList operator<(const Value& value) const;
    virtual ResultList operator==(const Value& value) const;
    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;

    Value::UP clone() const { return Value::UP(new StructValue(_values)); }
private:
    ValueMap _values;
};

} // select
} // document

