// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_nodes.h"
#include "node_visitor.h"

namespace vespalib {
namespace eval {
namespace nodes {

void TensorMap     ::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void TensorJoin    ::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void TensorMerge   ::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void TensorReduce  ::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void TensorRename  ::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void TensorConcat  ::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void TensorCellCast::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void TensorCreate  ::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void TensorLambda  ::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void TensorPeek    ::accept(NodeVisitor &visitor) const { visitor.visit(*this); }

} // namespace vespalib::eval::nodes
} // namespace vespalib::eval
} // namespace vespalib
