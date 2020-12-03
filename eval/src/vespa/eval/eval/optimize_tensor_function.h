// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib { class Stash; }

namespace vespalib::eval {

struct ValueBuilderFactory;
struct TensorFunction;

const TensorFunction &optimize_tensor_function(const ValueBuilderFactory &factory, const TensorFunction &function, Stash &stash);

} // namespace vespalib::eval
