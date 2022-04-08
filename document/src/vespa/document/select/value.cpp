// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "value.h"
#include "operator.h"
#include "variablemap.h"
#include <vespa/document/fieldvalue/fieldvalue.h>
#include <bitset>
#include <ostream>

namespace document::select {

ResultList
Value::globCompare(const Value& value) const
{
    return GlobOperator::GLOB.compareImpl(*this, value);
}

ResultList
Value::regexCompare(const Value& value) const
{
    return RegexOperator::REGEX.compareImpl(*this, value);
}

ResultList
Value::globTrace(const Value& value, std::ostream& trace) const
{
    return GlobOperator::GLOB.traceImpl(*this, value, trace);
}

ResultList
Value::regexTrace(const Value& value, std::ostream& trace) const
{
    return RegexOperator::REGEX.traceImpl(*this, value, trace);
}

ResultList
InvalidValue::operator<(const Value&) const
{
    return ResultList(Result::Invalid);
}

ResultList
InvalidValue::operator==(const Value&) const
{
    return ResultList(Result::Invalid);
}

void
InvalidValue::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "invalid";
}

ResultList
NullValue::operator<(const Value&) const
{
    return ResultList(Result::Invalid);
}

ResultList
NullValue::operator==(const Value& value) const
{
    const NullValue* nval(dynamic_cast<const NullValue*>(&value));
    if (nval != 0) return ResultList(Result::True);
    const InvalidValue* ival(dynamic_cast<const InvalidValue*>(&value));
    return ResultList(ival != 0 ? Result::Invalid : Result::False);
}


ResultList
NullValue::operator>(const Value &) const
{
    return ResultList(Result::Invalid);
}


ResultList
NullValue::operator>=(const Value &) const
{
    return ResultList(Result::Invalid);
}


ResultList
NullValue::operator<=(const Value &) const
{
    return ResultList(Result::Invalid);
}


void
NullValue::print(std::ostream& out, bool verbose,
                    const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "nil";
}

StringValue::StringValue(vespalib::stringref val)
    : Value(String),
      _value(val)
{
}

ResultList
StringValue::operator<(const Value& value) const
{
    const StringValue* val(dynamic_cast<const StringValue*>(&value));
    if (val == 0) return ResultList(Result::Invalid);
    return ResultList(Result::get(_value < val->_value));
}

ResultList
StringValue::operator==(const Value& value) const
{
    const StringValue* val(dynamic_cast<const StringValue*>(&value));
    if (val == 0) {
        const NullValue* nval(dynamic_cast<const NullValue*>(&value));
        return ResultList(nval == 0 ? Result::Invalid : Result::False);
    }
    return ResultList(Result::get(_value == val->_value));
}

void
StringValue::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "\"" << _value << "\"";
}

IntegerValue::IntegerValue(int64_t val, bool isBucketValue)
    : NumberValue(isBucketValue ? Bucket : Integer),
      _value(val)
{
}

ResultList
IntegerValue::operator<(const Value& value) const
{
    const NumberValue* val(dynamic_cast<const NumberValue*>(&value));
    if (val == 0) return ResultList(Result::Invalid);
    return val->operator>(*this);
}

ResultList
IntegerValue::operator==(const Value& value) const
{
    const NumberValue* val(dynamic_cast<const NumberValue*>(&value));
    if (val == 0) {
        const NullValue* nval(dynamic_cast<const NullValue*>(&value));
        return ResultList(nval == 0 ? Result::Invalid : Result::False);
    }
    return val->operator==(*this);
}

void
IntegerValue::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << _value << 'i';
}

FloatValue::FloatValue(double val)
    : NumberValue(Float),
      _value(val)
{
}

ResultList
FloatValue::operator<(const Value& value) const
{
    const NumberValue* val(dynamic_cast<const NumberValue*>(&value));
    if (val == 0) return ResultList(Result::Invalid);
    return val->operator>(*this);
}

ResultList
FloatValue::operator==(const Value& value) const
{
    const NumberValue* val(dynamic_cast<const NumberValue*>(&value));
    if (val == 0) {
        const NullValue* nval(dynamic_cast<const NullValue*>(&value));
        return ResultList(nval == 0 ? Result::Invalid : Result::False);
    }
    return val->operator==(*this);
}

void
FloatValue::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << _value << 'f';
}

ArrayValue::ArrayValue(std::vector<VariableValue> values)
    : Value(Array),
      _values(std::move(values))
{
}
ArrayValue::~ArrayValue() = default;

struct ArrayValue::EqualsComparator {
    bool operator()(std::size_t lhs, std::size_t rhs) const { return lhs == rhs; }
    ResultList operator()(const Value& lhs, const Value& rhs) const { return lhs == rhs; }
};

struct ArrayValue::NotEqualsComparator {
    bool operator()(std::size_t lhs, std::size_t rhs) const { return lhs != rhs; }
    ResultList operator()(const Value& lhs, const Value& rhs) const { return lhs != rhs; }
};

struct ArrayValue::LessThanComparator {
    bool operator()(std::size_t lhs, std::size_t rhs) const { return lhs < rhs; }
    ResultList operator()(const Value& lhs, const Value& rhs) const { return lhs < rhs; }
};

struct ArrayValue::GreaterThanComparator {
    bool operator()(std::size_t lhs, std::size_t rhs) const { return lhs > rhs; }
    ResultList operator()(const Value& lhs, const Value& rhs) const { return lhs > rhs; }
};

struct ArrayValue::LessThanOrEqualsComparator {
    bool operator()(std::size_t lhs, std::size_t rhs) const { return lhs <= rhs; }
    ResultList operator()(const Value& lhs, const Value& rhs) const { return lhs <= rhs; }
};

struct ArrayValue::GreaterThanOrEqualsComparator {
    bool operator()(std::size_t lhs, std::size_t rhs) const { return lhs >= rhs; }
    ResultList operator()(const Value& lhs, const Value& rhs) const { return lhs >= rhs; }
};

struct ArrayValue::GlobComparator {
    bool operator()(std::size_t lhs, std::size_t rhs) const { return lhs == rhs; }
    ResultList operator()(const Value& lhs, const Value& rhs) const {
        return GlobOperator::GLOB.compareImpl(lhs, rhs);
    }
};

struct ArrayValue::RegexComparator {
    bool operator()(std::size_t lhs, std::size_t rhs) const { return lhs == rhs; }
    ResultList operator()(const Value& lhs, const Value& rhs) const {
        return RegexOperator::REGEX.compareImpl(lhs, rhs);
    }
};


ResultList
ArrayValue::operator<(const Value& value) const
{
    return doCompare(value, LessThanComparator());
}

ResultList
ArrayValue::operator==(const Value& value) const
{
    return doCompare(value, EqualsComparator());
}

ResultList
ArrayValue::operator>(const Value& value) const
{
    return doCompare(value, GreaterThanComparator());
}

ResultList
ArrayValue::operator>=(const Value& value) const
{
    return doCompare(value, GreaterThanOrEqualsComparator());
}

ResultList
ArrayValue::operator<=(const Value& value) const
{
    return doCompare(value, LessThanOrEqualsComparator());
}

ResultList
ArrayValue::operator!=(const Value& value) const
{
    return doCompare(value, NotEqualsComparator());
}

ResultList
ArrayValue::globCompare(const Value& value) const
{
    return doCompare(value, GlobComparator());
}
ResultList
ArrayValue::regexCompare(const Value& value) const
{
    return doCompare(value, RegexComparator());
}

ResultList
ArrayValue::globTrace(const Value& value, std::ostream& trace) const
{
    trace << "Glob compare of lhs ArrayValue, rhs " << value << "\n";
    // TODO: have trace be propagated down to comparison ops?
    return doCompare(value, GlobComparator());
}

ResultList
ArrayValue::regexTrace(const Value& value, std::ostream& trace) const
{
    trace << "Regex compare of lhs ArrayValue, rhs " << value << "\n";
    // TODO: have trace be propagated down to comparison ops?
    return doCompare(value, RegexComparator());
}

void
ArrayValue::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "<no array representation in language yet>";
}

StructValue::StructValue(ValueMap values)
    : Value(Struct),
      _values(std::move(values))
{
}

StructValue::~StructValue() = default;

ResultList
StructValue::operator<(const Value& value) const
{
    const StructValue* val(dynamic_cast<const StructValue*>(&value));
    if (val == 0) {
        return ResultList(Result::Invalid);
    }
    ValueMap::const_iterator it1 = _values.begin();
    ValueMap::const_iterator it2 = val->_values.begin();
    while (it1 != _values.end() && it2 != val->_values.end()) {
        if (it1->first != it2->first) {
            return ResultList(it1->first < it2->first ? Result::True : Result::False);
        }
        ResultList result = (*it1->second == *it2->second);
        if (result == Result::True) {
            ++it1;
            ++it2;
            continue;
        }
        result = (*it1->second < *it2->second);
        return result;
    }
    if (it1 != _values.end() || it2 != val->_values.end()) {
        return ResultList(it1 == _values.end() ? Result::True : Result::False);
    }
    return ResultList(Result::False);
}

ResultList
StructValue::operator==(const Value& value) const
{
    const StructValue* val(dynamic_cast<const StructValue*>(&value));
    if (val == 0) {
        const NullValue* nval(dynamic_cast<const NullValue*>(&value));
        return ResultList(nval == 0 ? Result::Invalid : Result::False);
    }
    ValueMap::const_iterator it1 = _values.begin();
    ValueMap::const_iterator it2 = val->_values.begin();
    while (it1 != _values.end() && it2 != val->_values.end()) {
        if (it1->first != it2->first) {
            return ResultList(Result::False);
        }
        ResultList result = (*it1->second == *it2->second);
        if (result == Result::True) {
            ++it1;
            ++it2;
            continue;
        }
        return ResultList(Result::False);
    }
    if (it1 != _values.end() || it2 != val->_values.end()) {
        return ResultList(Result::False);
    }
    return ResultList(Result::True);
}

void
StructValue::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "<no struct representation in language yet>";
}

namespace {

fieldvalue::VariableMap
cloneMap(const fieldvalue::VariableMap &map) {
    fieldvalue::VariableMap m;
    for (const auto & item : map) {
        m.emplace(item.first, item.second);
    }
    return m;
}

}

template <typename Predicate>
ResultList
ArrayValue::doCompare(const Value& value, const Predicate& cmp) const
{
    if (value.getType() == Array) {
        // If comparing with an array, must match all.
        const ArrayValue* val(static_cast<const ArrayValue*>(&value));
        if (_values.size() != val->_values.size()) {
            return ResultList(cmp(_values.size(), val->_values.size())
                              ? Result::True : Result::False);
        }
        for (uint32_t i=0; i<_values.size(); ++i) {
            ResultList result = cmp(*_values[i].second, *val->_values[i].second);
            if (result == Result::False || result == Result::Invalid) {
                return result;
            }
        }
        return ResultList(Result::True);
    } else {
        ResultList results;

        std::bitset<3> resultForNoVariables;
        // If comparing with other value, must match one.
        for (const auto & item : _values) {
            const Result & result = cmp(*item.second, value).combineResults();
            if (item.first.empty()) {
                resultForNoVariables.set(result.toEnum());
            } else {
                results.add(cloneMap(item.first), result);
            }
        }
        for (uint32_t i(0); i < resultForNoVariables.size(); i++) {
            if (resultForNoVariables[i]) {
                results.add(fieldvalue::VariableMap(), Result::fromEnum(i));
            }
        }
        return results;
    }
}

}
