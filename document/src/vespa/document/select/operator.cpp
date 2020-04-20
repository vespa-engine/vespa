// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "operator.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/regex/regex.h>
#include <cassert>
#include <ostream>

#include <vespa/log/log.h>
LOG_SETUP(".document.select.operator");

namespace document::select {

Operator::OperatorMap Operator::_operators;

Operator::Operator(vespalib::stringref name)
    : _name(name)
{
    OperatorMap::iterator it = _operators.find(name);
    if (it != _operators.end()) {
        LOG_ABORT("unknown operator, should not happen");
    }
    _operators[_name] = this;
}

const Operator&
Operator::get(vespalib::stringref name)
{
    OperatorMap::iterator it = _operators.find(name);
    if (it == _operators.end()) {
        LOG_ABORT("unknown operator, should not happen");
    }
    return *it->second;
}

void
Operator::print(std::ostream& out, bool verbose,
                const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << _name;
}

ResultList
FunctionOperator::compare(const Value& a, const Value& b) const
{
    return (a.*_comparator)(b);
}

ResultList
FunctionOperator::trace(const Value& a, const Value& b,
                        std::ostream& out) const
{
    ResultList result = (a.*_comparator)(b);
    out << "Operator(" << getName() << ") - Result was "
        << result << ".\n";
    return result;
}

const FunctionOperator
FunctionOperator::GT(">", &Value::operator>);

const FunctionOperator
FunctionOperator::GEQ(">=", &Value::operator>=);

const FunctionOperator
FunctionOperator::EQ("==", &Value::operator==);

const FunctionOperator
FunctionOperator::LEQ("<=", &Value::operator<=);

const FunctionOperator
FunctionOperator::LT("<", &Value::operator<);

const FunctionOperator
FunctionOperator::NE("!=", &Value::operator!=);

RegexOperator::RegexOperator(vespalib::stringref name)
    : Operator(name)
{
}

ResultList
RegexOperator::compare(const Value& a, const Value& b) const
{
    return a.regexCompare(b);
}

ResultList
RegexOperator::trace(const Value& a, const Value& b, std::ostream& out) const
{
    return a.regexTrace(b, out);
}

ResultList
RegexOperator::compareImpl(const Value& a, const Value& b) const
{
    const auto* left(dynamic_cast<const StringValue*>(&a));
    const auto* right(dynamic_cast<const StringValue*>(&b));
    if (left == nullptr || right == nullptr) {
        return ResultList(Result::Invalid);
    }
    return match(left->getValue(), right->getValue());
}

ResultList
RegexOperator::traceImpl(const Value& a, const Value& b, std::ostream& out) const
{
    const auto* left(dynamic_cast<const StringValue*>(&a));
    const auto* right(dynamic_cast<const StringValue*>(&b));
    if (left == nullptr) {
        out << "Operator(" << getName() << ") - Left value not a string. "
            << "Returning invalid.\n";
        return ResultList(Result::Invalid);
    }
    if (right == nullptr) {
        out << "Operator(" << getName() << ") - Right value not a string. "
            << "Returning invalid.\n";
        return ResultList(Result::Invalid);
    }
    ResultList result = match(left->getValue(), right->getValue());
    out << "Operator(" << getName() << ")(" << left->getValue() << ", "
        << right->getValue() << ") - Result was " << result << "\n";
    return result;
}

ResultList
RegexOperator::match(const vespalib::string& val, vespalib::stringref expr) const
{
    if (expr.empty()) {
        return ResultList(Result::True); // Should we catch this in parsing?
    }
    return ResultList(Result::get(
            vespalib::Regex::partial_match(std::string_view(val.data(), val.size()),
                                           std::string_view(expr.data(), expr.size()))));
}

const RegexOperator RegexOperator::REGEX("=~");

GlobOperator::GlobOperator(vespalib::stringref name)
    : RegexOperator(name)
{
}

ResultList
GlobOperator::compare(const Value& a, const Value& b) const
{
    return a.globCompare(b);
}

ResultList
GlobOperator::trace(const Value& a, const Value& b, std::ostream& out) const
{
    return a.globTrace(b, out);
}

ResultList
GlobOperator::compareImpl(const Value& a, const Value& b) const
{
    const auto* right(dynamic_cast<const StringValue*>(&b));
    // Fall back to operator== if it isn't string matching
    if (right == nullptr) {
        return FunctionOperator::EQ.compare(a, b);
    }
    const auto* left(dynamic_cast<const StringValue*>(&a));
    if (left == nullptr) {
        return ResultList(Result::Invalid);
    }
    vespalib::string regex(convertToRegex(right->getValue()));
    return match(left->getValue(), regex);
}

ResultList
GlobOperator::traceImpl(const Value& a, const Value& b, std::ostream& ost) const
{
    const auto* right(dynamic_cast<const StringValue*>(&b));
    // Fall back to operator== if it isn't string matching
    if (right == nullptr) {
        ost << "Operator(" << getName() << ") - Right val not a string, "
            << "falling back to == behavior.\n";
        return FunctionOperator::EQ.trace(a, b, ost);
    }
    const auto* left(dynamic_cast<const StringValue*>(&a));
    if (left == nullptr) {
        ost << "Operator(" << getName() << ") - Left value is not a string, "
            << "returning invalid.\n";
        return ResultList(Result::Invalid);
    }
    vespalib::string regex(convertToRegex(right->getValue()));
    ost << "Operator(" << getName() << ") - Converted glob expression '"
        << right->getValue() << "' to regex '"  << regex << "'.\n";
    return match(left->getValue(), regex);
}

namespace {

// Returns the number of consecutive wildcard ('*') characters found from
// _and including_ the character at `i`, i.e. the wildcard run length.
size_t wildcard_run_length(size_t i, vespalib::stringref str) {
    size_t n = 0;
    for (; (i < str.size()) && (str[i] == '*'); ++n, ++i) {}
    return n;
}

}

vespalib::string
GlobOperator::convertToRegex(vespalib::stringref globpattern)
{
    if (globpattern.empty()) {
        return "^$"; // Empty glob can only match the empty string.
    }
    vespalib::asciistream ost;
    size_t i = 0;
    if (globpattern[0] != '*') {
        ost << '^';
    } else {
        i += wildcard_run_length(0, globpattern); // Skip entire prefix wildcard run.
    }
    const size_t n = globpattern.size();
    for (; i < n; ++i) {
        switch(globpattern[i]) {
        case '*':
            i += wildcard_run_length(i, globpattern) - 1; // -1 since we always inc by 1 anyway.
            if (i != (n - 1)) { // Don't emit trailing wildcard.
                ost << ".*";
            }
            break;
        case '?':
            ost << '.';
            break;
        case '^':
        case '$':
        case '|':
        case '{':
        case '}':
        case '(':
        case ')':
        case '[':
        case ']':
        case '\\':
        case '+':
        case '.':
            ost << '\\' << globpattern[i];
            break;
        // Are there other regex special chars we need to escape?
        default: ost << globpattern[i];
        }
    }
    if (globpattern[n - 1] != '*') {
        ost << '$';
    }
    return ost.str();
}

bool
GlobOperator::containsVariables(vespalib::stringref expression)
{
    for (size_t i=0, n=expression.size(); i<n; ++i) {
        if (expression[i] == '*' || expression[i] == '?') {
            return true;
        }
    }
    return false;
}

const GlobOperator GlobOperator::GLOB("=");

}
