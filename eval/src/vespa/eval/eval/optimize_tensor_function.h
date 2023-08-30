// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <functional>

namespace vespalib { class Stash; }

namespace vespalib::eval {

struct OptimizeTensorFunctionOptions {
    bool allow_universal_dot_product;
    OptimizeTensorFunctionOptions() noexcept;
    ~OptimizeTensorFunctionOptions();
};

struct ValueBuilderFactory;
struct TensorFunction;

const TensorFunction &optimize_tensor_function(const ValueBuilderFactory &factory, const TensorFunction &function, Stash &stash,
                                               const OptimizeTensorFunctionOptions &options);
const TensorFunction &optimize_tensor_function(const ValueBuilderFactory &factory, const TensorFunction &function, Stash &stash);

using tensor_function_optimizer = std::function<const TensorFunction &(const TensorFunction &expr, Stash &stash)>;
const TensorFunction &apply_tensor_function_optimizer(const TensorFunction &function, tensor_function_optimizer optimizer, Stash &stash, size_t *count = nullptr);

} // namespace vespalib::eval
