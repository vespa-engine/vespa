// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "interpreted_function.h"
#include <vector>

namespace vespalib { class Stash; }

namespace vespalib::eval {

class TensorFunction;

std::vector<InterpretedFunction::Instruction> compile_tensor_function(const TensorFunction &function, Stash &stash);

} // namespace vespalib::eval
