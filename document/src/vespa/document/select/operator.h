// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class document::select::Operator
 * @ingroup select
 *
 * @brief An operator that can be used to compare values
 *
 * @author H�kon Humberset
 * @date 2007-04-20
 * @version $Id$
 */

#pragma once

#include "value.h"
#include <vespa/vespalib/stllike/hash_map.h>

namespace document::select {

class Operator : public Printable {
private:
    using OperatorMap = vespalib::hash_map<vespalib::string, const Operator*>;
    static OperatorMap _operators;
    vespalib::string _name;

public:
    Operator(vespalib::stringref name);
    virtual ~Operator() {}

    virtual ResultList compare(const Value&, const Value&) const = 0;
    virtual ResultList trace(const Value&, const Value&,
                         std::ostream& trace) const = 0;
    const vespalib::string& getName() const { return _name; }

    static const Operator& get(vespalib::stringref name);

    bool operator==(const Operator& op) const
        { return (_name == op._name); }
    bool operator!=(const Operator& op) const
        { return (_name != op._name); }

    void print(std::ostream&, bool verbose, const std::string& indent) const override;
};

class FunctionOperator : public Operator {
private:
    ResultList (Value::*_comparator)(const Value&) const;

public:
    FunctionOperator(vespalib::stringref name,
                ResultList (Value::*comparator)(const Value&) const)
        : Operator(name), _comparator(comparator) {}

    ResultList compare(const Value& a, const Value& b) const override;
    ResultList trace(const Value&, const Value&, std::ostream& trace) const override;

    static const FunctionOperator GT;
    static const FunctionOperator GEQ;
    static const FunctionOperator EQ;
    static const FunctionOperator LEQ;
    static const FunctionOperator LT;
    static const FunctionOperator NE;
};

class RegexOperator : public Operator {
public:
    RegexOperator(vespalib::stringref name);

    // Delegates to Value::regexCompare
    ResultList compare(const Value& a, const Value& b) const override;
    ResultList trace(const Value&, const Value&, std::ostream& trace) const override;
    ResultList match(const vespalib::string & val, vespalib::stringref expr) const;

    static const RegexOperator REGEX;

private:
    friend class Value;
    friend class ArrayValue;
    // Note: not virtual, must be called on known type
    ResultList compareImpl(const Value& a, const Value& b) const;
    ResultList traceImpl(const Value&, const Value&, std::ostream& trace) const;
};

class GlobOperator : public RegexOperator {
public:
    GlobOperator(vespalib::stringref name);

    // Delegates to Value::globCompare
    ResultList compare(const Value& a, const Value& b) const override;
    ResultList trace(const Value&, const Value&, std::ostream& trace) const override;
    /**
     * Converts a standard glob expression into a regular expression string,
     * supporting the following glob semantics:
     *   '*' matches 0-n arbitrary characters
     *   '?' matches exactly 1 arbitrary character
     * This code simplifies the resulting regex as much as possible to help
     * minimize the number of possible catastrophic backtracking cases that
     * can be triggered by wildcard regexes.
     *
     * The following simplifications are currently performed:
     *   - ''      -> /^$/   (empty string match)
     *   '*'       -> //     (any string match)
     *   - '*foo*' -> /foo/  (substring match)
     *   - '*foo'  -> /foo$/ (suffix match)
     *   - 'foo*'  -> /^foo/ (prefix match)
     *   - collapsing runs of consecutive '*' wildcards into a single
     *     wildcard. *** is identical to ** which is identical to * etc,
     *     as all these match 0-n characters each. This also works with
     *     simplification, i.e. '***foo***' -> /foo/ and '***' -> //
     */
    static vespalib::string convertToRegex(vespalib::stringref globpattern);
    static bool containsVariables(vespalib::stringref expression);

    static const GlobOperator GLOB;
private:
    friend class Value;
    friend class ArrayValue;
    // Note: not virtual, must be called on known type
    ResultList compareImpl(const Value& a, const Value& b) const;
    ResultList traceImpl(const Value&, const Value&, std::ostream& trace) const;
};

}
