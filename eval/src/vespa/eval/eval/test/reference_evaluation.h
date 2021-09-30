// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_spec.h>
#include <vector>

namespace vespalib::eval { class Function; }

namespace vespalib::eval::test {

struct ReferenceEvaluation {
    static TensorSpec eval(const Function &function, const std::vector<TensorSpec> &params);
};

} // namespace
