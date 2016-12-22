// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "constant.h"
#include "visitor.h"

namespace document {
namespace select {

Constant::Constant(const vespalib::stringref & value)
    : Node(value),
      _value(false)
{
    if (value.size() == 4 &&
        (value[0] & 0xdf) == 'T' &&
        (value[1] & 0xdf) == 'R' &&
        (value[2] & 0xdf) == 'U' &&
        (value[3] & 0xdf) == 'E')
    {
        _value = true;
    } else if (value.size() == 5 &&
        (value[0] & 0xdf) == 'F' &&
        (value[1] & 0xdf) == 'A' &&
        (value[2] & 0xdf) == 'L' &&
        (value[3] & 0xdf) == 'S' &&
        (value[4] & 0xdf) == 'E')
    {
        _value = false;
    } else {
        assert(false);
    }
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
Constant::print(std::ostream& out, bool,
                const std::string&) const
{
    if (_parentheses) out << '(';
    out << _name;
    if (_parentheses) out << ')';
}

} // select
} // document
