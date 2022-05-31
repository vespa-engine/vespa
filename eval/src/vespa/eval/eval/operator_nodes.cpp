// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "operator_nodes.h"
#include "node_visitor.h"

namespace vespalib::eval::nodes {

Operator::Operator(const vespalib::string &op_str_in, int priority_in, Order order_in)
    : _op_str(op_str_in),
      _priority(priority_in),
      _order(order_in),
      _lhs(),
      _rhs(),
      _is_const_double(false)
{}

Operator::~Operator() = default;

template <typename T> void OperatorHelper<T>::accept(NodeVisitor &visitor) const {
    visitor.visit(static_cast<const T&>(*this));
}

OperatorRepo OperatorRepo::_instance;
OperatorRepo::OperatorRepo() : _map(), _max_size(0) {
    add(nodes::Add());
    add(nodes::Sub());
    add(nodes::Mul());
    add(nodes::Div());
    add(nodes::Mod());
    add(nodes::Pow());
    add(nodes::Equal());
    add(nodes::NotEqual());
    add(nodes::Approx());
    add(nodes::Less());
    add(nodes::LessEqual());
    add(nodes::Greater());
    add(nodes::GreaterEqual());
    add(nodes::And());
    add(nodes::Or());
}

}
