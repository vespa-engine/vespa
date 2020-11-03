// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "engine_or_factory.h"

namespace vespalib { class Stash; }

namespace vespalib::eval {

struct TensorFunction;

const TensorFunction &optimize_tensor_function(EngineOrFactory engine, const TensorFunction &function, Stash &stash);

} // namespace vespalib::eval
