// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "operator_nodes.h"
#include "node_visitor.h"

namespace vespalib {
namespace eval {
namespace nodes {

template <typename T> void OperatorHelper<T>::accept(NodeVisitor &visitor) const {
    visitor.visit(static_cast<const T&>(*this));
}

OperatorRepo OperatorRepo::_instance;
OperatorRepo::OperatorRepo() : _map(), _max_size(0) {
    add(nodes::Add());
    add(nodes::Sub());
    add(nodes::Mul());
    add(nodes::Div());
    add(nodes::Pow());
    add(nodes::Equal());
    add(nodes::NotEqual());
    add(nodes::Approx());
    add(nodes::Less());
    add(nodes::LessEqual());
    add(nodes::Greater());
    add(nodes::GreaterEqual());
    add(nodes::In());
    add(nodes::And());
    add(nodes::Or());
}

vespalib::string
In::dump(DumpContext &ctx) const
{
    vespalib::string str;
    str += "(";
    str += lhs().dump(ctx);
    str += " in ";
    str += rhs().dump(ctx);
    str += ")";
    return str;
}

} // namespace vespalib::eval::nodes
} // namespace vespalib::eval
} // namespace vespalib
