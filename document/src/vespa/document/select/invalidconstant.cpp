// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "invalidconstant.h"
#include "visitor.h"
#include <ostream>

namespace document::select {

InvalidConstant::InvalidConstant(vespalib::stringref value)
    : Node(value)
{ }

ResultList
InvalidConstant::trace(const Context&, std::ostream& ost) const
{
    ost << "InvalidConstant - " << Result::Invalid << ".\n";
    return ResultList(Result::Invalid);
}


void
InvalidConstant::visit(Visitor &v) const
{
    v.visitInvalidConstant(*this);
}


void
InvalidConstant::print(std::ostream& out, bool, const std::string&) const
{
    if (_parentheses) out << '(';
    out << _name;
    if (_parentheses) out << ')';
}

}
