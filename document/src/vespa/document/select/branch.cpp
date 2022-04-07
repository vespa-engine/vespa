// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "branch.h"
#include "visitor.h"
#include <cassert>
#include <ostream>

namespace document::select {

And::And(std::unique_ptr<Node> left, std::unique_ptr<Node> right, const char* name)
    : Branch(name ? name : "and", std::max(left->max_depth(), right->max_depth()) + 1),
      _left(std::move(left)),
      _right(std::move(right))
{
    assert(_left);
    assert(_right);
}


void
And::visit(Visitor &v) const
{
    v.visitAndBranch(*this);
}


void
And::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    if (_parentheses) out << '(';
    _left->print(out, verbose, indent);
    out << " " << _name << " ";
    _right->print(out, verbose, indent);
    if (_parentheses) out << ')';
}

namespace {
    template<typename T>
    ResultList traceAndValue(const T& value, std::ostream& out,
                         const Node& leftNode, const Node& rightNode)
    {
        out << "And - Left branch returned " << leftNode.contains(value) << ".\n";
        out << "And - Right branch returned " << rightNode.contains(value) << ".\n";
        return leftNode.contains(value) && rightNode.contains(value);
    }
}

ResultList
And::trace(const Context& context, std::ostream& out) const
{
    return traceAndValue(context, out, *_left, *_right);
}

Or::Or(std::unique_ptr<Node> left, std::unique_ptr<Node> right, const char* name)
    : Branch(name ? name : "or", std::max(left->max_depth(), right->max_depth()) + 1),
      _left(std::move(left)),
      _right(std::move(right))
{
    assert(_left.get());
    assert(_right.get());
}


void
Or::visit(Visitor &v) const
{
    v.visitOrBranch(*this);
}


void
Or::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    if (_parentheses) out << '(';
    _left->print(out, verbose, indent);
    out << " " << _name << " ";
    _right->print(out, verbose, indent);
    if (_parentheses) out << ')';
}

namespace {
    template<typename T>
    ResultList traceOrValue(const T& value, std::ostream& out,
                         const Node& leftNode, const Node& rightNode)
    {
        out << "Or - Left branch returned " << leftNode.contains(value) << ".\n";
        out << "Or - Right branch returned " << rightNode.contains(value) << ".\n";
        return leftNode.contains(value) || rightNode.contains(value);
    }
}

ResultList
Or::trace(const Context& context, std::ostream& out) const
{
    return traceOrValue(context, out, *_left, *_right);
}

Not::Not(std::unique_ptr<Node> child, const char* name)
    : Branch(name ? name : "not", child->max_depth() + 1),
      _child(std::move(child))
{
    assert(_child.get());
}


void
Not::visit(Visitor &v) const
{
    v.visitNotBranch(*this);
}

void
Not::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    if (_parentheses) out << '(';
    out << _name << " ";
    _child->print(out, verbose, indent);
    if (_parentheses) out << ')';
}

namespace {
    template<typename T>
    ResultList traceNotValue(const T& value, std::ostream& out, const Node& node)
    {
        out << "Not - Child returned " << node.contains(value)
            << ". Returning opposite.\n";
        return !node.contains(value);
    }
}

ResultList
Not::trace(const Context& context, std::ostream& out) const
{
    return traceNotValue(context, out, *_child);
}

}
