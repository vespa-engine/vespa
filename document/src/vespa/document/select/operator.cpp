// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "operator.h"
#include <vespa/vespalib/util/regexp.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".document.select.operator");

namespace document::select {

Operator::OperatorMap Operator::_operators;

Operator::Operator(const vespalib::stringref & name)
    : _name(name)
{
    OperatorMap::iterator it = _operators.find(name);
    if (it != _operators.end()) {
        LOG_ABORT("unknown operator, should not happen");
    }
    _operators[_name] = this;
}

const Operator&
Operator::get(const vespalib::stringref & name)
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

RegexOperator::RegexOperator(const vespalib::stringref & name)
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
    const StringValue* left(dynamic_cast<const StringValue*>(&a));
    const StringValue* right(dynamic_cast<const StringValue*>(&b));
    if (left == 0 || right == 0) return ResultList(Result::Invalid);
    return match(left->getValue(), right->getValue());
}

ResultList
RegexOperator::traceImpl(const Value& a, const Value& b, std::ostream& out) const
{
    const StringValue* left(dynamic_cast<const StringValue*>(&a));
    const StringValue* right(dynamic_cast<const StringValue*>(&b));
    if (left == 0) {
        out << "Operator(" << getName() << ") - Left value not a string. "
            << "Returning invalid.\n";
        return ResultList(Result::Invalid);
    }
    if (right == 0) {
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
RegexOperator::match(const vespalib::string& val, const vespalib::stringref & expr) const
{
        // Should we catch this in parsing?
    if (expr.size() == 0) return ResultList(Result::True);
    vespalib::Regexp expression(expr);
    return ResultList(Result::get(expression.match(val)));
}

const RegexOperator RegexOperator::REGEX("=~");

GlobOperator::GlobOperator(const vespalib::stringref & name)
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
    const StringValue* right(dynamic_cast<const StringValue*>(&b));
        // Fall back to operator== if it isn't string matching
    if (right == 0) {
        return FunctionOperator::EQ.compare(a, b);
    }
    const StringValue* left(dynamic_cast<const StringValue*>(&a));
    if (left == 0) return ResultList(Result::Invalid);
    vespalib::string regex(convertToRegex(right->getValue()));
    return match(left->getValue(), regex);
}

ResultList
GlobOperator::traceImpl(const Value& a, const Value& b, std::ostream& ost) const
{
    const StringValue* right(dynamic_cast<const StringValue*>(&b));
        // Fall back to operator== if it isn't string matching
    if (right == 0) {
        ost << "Operator(" << getName() << ") - Right val not a string, "
            << "falling back to == behavior.\n";
        return FunctionOperator::EQ.trace(a, b, ost);
    }
    const StringValue* left(dynamic_cast<const StringValue*>(&a));
    if (left == 0) {
        ost << "Operator(" << getName() << ") - Left value is not a string, "
            << "returning invalid.\n";
        return ResultList(Result::Invalid);
    }
    vespalib::string regex(convertToRegex(right->getValue()));
    ost << "Operator(" << getName() << ") - Converted glob expression '"
        << right->getValue() << "' to regex '"  << regex << "'.\n";
    return match(left->getValue(), regex);
}

vespalib::string
GlobOperator::convertToRegex(const vespalib::stringref & globpattern) const
{
    vespalib::asciistream ost;
    ost << '^';
    for(uint32_t i=0, n=globpattern.size(); i<n; ++i) {
        switch(globpattern[i]) {
            case '*': ost << ".*";
                      break;
            case '?': ost << ".";
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
            case '.': ost << '\\' << globpattern[i];
                      break;
                // Are there other regex special chars we need to escape?
            default: ost << globpattern[i];
        }
    }
    ost << '$';
    return ost.str();
}

bool
GlobOperator::containsVariables(const vespalib::stringref & expression)
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
