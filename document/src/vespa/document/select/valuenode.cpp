// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "valuenode.h"
#include <ostream>

namespace document::select {

std::unique_ptr<Value>
ValueNode::traceValue(const Context &context, std::ostream &out) const {
    return defaultTrace(getValue(context), out);
}

std::unique_ptr<Value>
ValueNode::defaultTrace(std::unique_ptr<Value> val, std::ostream& out) const
{
    out << "Returning value " << *val << ".\n";
    return val;
}

}

