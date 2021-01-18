// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Tensor function optimizer for efficient non-transposing renames.
 **/
struct FastRenameOptimizer {
    static bool is_stable_rename(const ValueType &from_type, const ValueType &to_type,
                                 const std::vector<vespalib::string> &from,
                                 const std::vector<vespalib::string> &to);
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace vespalib::eval
