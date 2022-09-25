// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "compare.h"
#include "valuenode.h"
#include "visitor.h"
#include <vespa/document/datatype/datatype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/util/stringutil.h>
#include <ostream>

namespace document::select {

Compare::Compare(std::unique_ptr<ValueNode> left,
                 const Operator& op,
                 std::unique_ptr<ValueNode> right,
                 const BucketIdFactory& bucketIdFactory)
    : Node("Compare", std::max(left->max_depth(), right->max_depth()) + 1),
      _left(std::move(left)),
      _right(std::move(right)),
      _operator(op),
      _bucketIdFactory(bucketIdFactory)
{
}

Compare::~Compare() = default;

Node::UP
Compare::clone() const
{
    return wrapParens(new Compare(_left->clone(),
                                  _operator,
                                  _right->clone(),
                                  _bucketIdFactory));
}

namespace {

    template<typename T>
    ResultList containsValue(const T& value, const ValueNode& l, const ValueNode& r,
                         const Operator& op)
    {
        std::unique_ptr<Value> left(l.getValue(value));
        std::unique_ptr<Value> right(r.getValue(value));
        if (left->getType() == Value::Bucket
            || right->getType() == Value::Bucket)
        {
            Value& bVal(left->getType() == Value::Bucket ? *left : *right);
            Value& nVal(left->getType() == Value::Bucket ? *right : *left);
            if (nVal.getType() == Value::Integer
                && (op == FunctionOperator::EQ || op == FunctionOperator::NE
                    || op == GlobOperator::GLOB))
            {
                document::BucketId b( static_cast<IntegerValue&>(bVal).getValue());
                document::BucketId s( static_cast<IntegerValue&>(nVal).getValue());

                if (op == FunctionOperator::NE) {
                    return ! ResultList(Result::get(s.contains(b)));
                }
                return ResultList(Result::get(s.contains(b)));
            } else {
                return ResultList(Result::Invalid);
            }
        }
        return op.compare(*left, *right);
    }

    template<typename T>
    ResultList traceValue(const T& value, const ValueNode& l, const ValueNode& r,
                      const Operator& op, std::ostream& out)
    {
        std::unique_ptr<Value> left(l.traceValue(value, out));
        std::unique_ptr<Value> right(r.traceValue(value, out));
        if (left->getType() == Value::Bucket
            || right->getType() == Value::Bucket)
        {
            Value& bVal(left->getType() == Value::Bucket ? *left : *right);
            Value& nVal(left->getType() == Value::Bucket ? *right : *left);
            if (nVal.getType() == Value::Integer
                && (op == FunctionOperator::EQ || op == FunctionOperator::NE
                    || op == GlobOperator::GLOB))
            {
                document::BucketId b(
                        static_cast<IntegerValue&>(bVal).getValue());
                document::BucketId s(
                        static_cast<IntegerValue&>(nVal).getValue());

                ResultList resultList = (op == FunctionOperator::NE)
                        ? !ResultList(Result::get(s.contains(b)))
                        : ResultList(Result::get(s.contains(b)));

                out << "Checked if " << b.toString() << " is ";
                if (op == FunctionOperator::NE) { out << "not "; }
                out << "contained in " << s.toString()
                    << ". Result was " << resultList << ".\n";
                return resultList;
            } else {
                out << "Compare type " << left->getType() << " vs "
                    << right->getType() << " - Result is thus invalid.\n";

                return ResultList(Result::Invalid);
            }
        }
        out << "Compare - Left value ";
        left->print(out, false, "");
        out << " " << op.getName() << " right value ";
        right->print(out, false, "");
        out << "\n";
        ResultList result = op.trace(*left, *right, out);
        out << "Result from compare was " << result << ".\n";
        return result;
    }
}

ResultList
Compare::contains(const Context& context) const
{
    return containsValue<Context>(context, *_left, *_right, _operator);
}

ResultList
Compare::trace(const Context& context, std::ostream& out) const
{
    return traceValue(context, *_left, *_right, _operator, out);
}


void
Compare::visit(Visitor &v) const
{
    v.visitComparison(*this);
}


void
Compare::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    if (_parentheses) out << '(';
    _left->print(out, verbose, indent);
    out << " ";
    _operator.print(out, verbose, indent);
    out << " ";
    _right->print(out, verbose, indent);
    if (_parentheses) out << ')';
}

}
