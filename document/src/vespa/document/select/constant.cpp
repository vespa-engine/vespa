// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "constant.h"
#include "visitor.h"
#include <ostream>

namespace document::select {

Constant::Constant(bool value)
    : Node(value ? "true" : "false"), // TODO remove required name from Node
      _value(value)
{
}

ResultList
Constant::trace(const Context&, std::ostream& ost) const
{
    ost << "Constant - " << Result::get(_value) << ".\n";
    return ResultList(Result::get(_value));
}


void
Constant::visit(Visitor &v) const
{
    v.visitConstant(*this);
}


void
Constant::print(std::ostream& out, bool, const std::string&) const
{
    if (_parentheses) out << '(';
    out << _name;
    if (_parentheses) out << ')';
}

}
