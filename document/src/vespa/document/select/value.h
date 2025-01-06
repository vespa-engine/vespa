// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

#include "resultlist.h"
#include <iosfwd>
#include <map>
#include <memory>
#include <string>
#include <vector>

namespace document::select {

class Value : public document::Printable
{
public:
    using SP = std::shared_ptr<Value>;
    using UP = std::unique_ptr<Value>;
    enum class Type { Invalid, Null, String, Integer, Float, Array, Struct, Bucket, Tensor };

    explicit Value(Type t) : _type(t) {}
    ~Value() override = default;

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

std::ostream& operator<<(std::ostream&, Value::Type);

class InvalidValue : public Value
{
public:
    InvalidValue() : Value(Type::Invalid) {}

    ResultList operator<(const Value&) const override;
    ResultList operator==(const Value&) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

class NullValue : public Value
{
public:
    NullValue() : Value(Type::Null) {}

    ResultList operator<(const Value&) const override;
    ResultList operator==(const Value&) const override;
    ResultList operator>(const Value &) const override;
    ResultList operator>=(const Value &) const override;
    ResultList operator<=(const Value &) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

class StringValue : public Value
{
    std::string _value;

public:
    StringValue(std::string_view val);

    const std::string& getValue() const { return _value; }
    ResultList operator<(const Value& value) const override;
    ResultList operator==(const Value& value) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

class IntegerValue;
class FloatValue;

class NumberValue : public Value
{
public:
    using CommonValueType = double;

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
    using ValueType = int64_t;

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
    using ValueType = double;

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
    using ValueMap = std::map<std::string, Value::SP>;
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

// We currently only support checking for tensor field _presence_ as part of
// document selections (i.e. via null-checks). All other interactions with tensor
// fields will yield Invalid.
class TensorValue : public Value {
public:
    TensorValue();
    ~TensorValue() override;

    ResultList operator<(const Value&) const override;
    ResultList operator==(const Value&) const override;
    ResultList operator!=(const Value&) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

}
