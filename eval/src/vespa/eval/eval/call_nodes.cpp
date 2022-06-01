// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "call_nodes.h"
#include "node_visitor.h"

namespace vespalib::eval::nodes {

Call::~Call() = default;


template <typename T> void CallHelper<T>::accept(NodeVisitor &visitor) const {
    visitor.visit(static_cast<const T&>(*this));
}

CallRepo CallRepo::_instance;
CallRepo::CallRepo() : _map() {
    add(nodes::Cos());
    add(nodes::Sin());
    add(nodes::Tan());
    add(nodes::Cosh());
    add(nodes::Sinh());
    add(nodes::Tanh());
    add(nodes::Acos());
    add(nodes::Asin());
    add(nodes::Atan());
    add(nodes::Exp());
    add(nodes::Log10());
    add(nodes::Log());
    add(nodes::Sqrt());
    add(nodes::Ceil());
    add(nodes::Fabs());
    add(nodes::Floor());
    add(nodes::Atan2());
    add(nodes::Ldexp());
    add(nodes::Pow2());
    add(nodes::Fmod());
    add(nodes::Min());
    add(nodes::Max());
    add(nodes::IsNan());
    add(nodes::Relu());
    add(nodes::Sigmoid());
    add(nodes::Elu());
    add(nodes::Erf());
    add(nodes::Bit());
    add(nodes::Hamming());
}

}
