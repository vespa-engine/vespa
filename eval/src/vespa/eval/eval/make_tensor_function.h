// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "engine_or_factory.h"

namespace vespalib { class Stash; }

namespace vespalib::eval {

class NodeTypes;
struct TensorFunction;

namespace nodes { struct Node; }

const TensorFunction &make_tensor_function(EngineOrFactory engine, const nodes::Node &root, const NodeTypes &types, Stash &stash);

} // namespace vespalib::eval
