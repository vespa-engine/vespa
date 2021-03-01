// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/aggr.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/tensor_function.h>

#include <vector>
#include <map>
#include <variant>
#include <functional>

namespace vespalib::eval {

struct ReferenceOperations {
    using map_fun_t = std::function<double(double)>;
    using join_fun_t = std::function<double(double,double)>;
    using lambda_fun_t = std::function<double(const std::vector<size_t> &dimension_indexes)>;

    // mapping from cell address to index of child that computes the cell value
    using CreateSpec = tensor_function::Create::Spec;

    // mapping from dimension name to verbatim label or child index.
    // Note: child 0 is the input param, so indexes in the spec must
    // start at 1.
    using PeekSpec = tensor_function::Peek::Spec;

    static TensorSpec cell_cast(const TensorSpec &a, CellType to);
    static TensorSpec concat(const TensorSpec &a, const TensorSpec &b, const std::string &concat_dim);
    static TensorSpec create(const vespalib::string &type, const CreateSpec &spec, const std::vector<TensorSpec> &children);
    static TensorSpec join(const TensorSpec &a, const TensorSpec &b, join_fun_t function);
    static TensorSpec map(const TensorSpec &a, map_fun_t func);
    static TensorSpec merge(const TensorSpec &a, const TensorSpec &b, join_fun_t fun);
    static TensorSpec peek(const PeekSpec &spec, const std::vector<TensorSpec> &children);
    static TensorSpec reduce(const TensorSpec &a, Aggr aggr, const std::vector<vespalib::string> &dims);
    static TensorSpec rename(const TensorSpec &a, const std::vector<vespalib::string> &from, const std::vector<vespalib::string> &to);
    static TensorSpec lambda(const vespalib::string &type, lambda_fun_t fun);
};

} // namespace
