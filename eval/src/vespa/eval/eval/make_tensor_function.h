// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib { class Stash; }

namespace vespalib::eval {

class TensorEngine;
class NodeTypes;
class TensorFunction;

namespace nodes { class Node; }

const TensorFunction &make_tensor_function(const TensorEngine &engine, const nodes::Node &root, const NodeTypes &types, Stash &stash);

} // namespace vespalib::eval
