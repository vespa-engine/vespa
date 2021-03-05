// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_lambda_peek_optimizer.h"
#include "dense_lambda_peek_function.h"
#include "dense_cell_range_function.h"
#include <vespa/eval/instruction/replace_type_function.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/node_tools.h>
#include <vespa/eval/eval/basic_nodes.h>
#include <vespa/eval/eval/operator_nodes.h>
#include <vespa/eval/eval/call_nodes.h>
#include <vespa/eval/eval/tensor_nodes.h>
#include <vespa/eval/eval/llvm/compile_cache.h>
#include <optional>

using namespace vespalib::eval::nodes;

namespace vespalib::eval {

namespace {

// 'simple peek': deterministic peek into a single parameter with
//                compilable dimension index expressions.
const TensorPeek *find_simple_peek(const tensor_function::Lambda &lambda) {
    const Function &function = lambda.lambda();
    const size_t num_dims = lambda.result_type().dimensions().size();
    auto peek = as<TensorPeek>(function.root());
    if (peek && (function.num_params() == (num_dims + 1))) {
        auto param = as<Symbol>(peek->get_child(0));
        if (param && (param->id() == num_dims)) {
            for (size_t i = 1; i < peek->num_children(); ++i) {
                const Node &dim_expr = peek->get_child(i);
                if (NodeTools::min_num_params(dim_expr) > num_dims) {
                    return nullptr;
                }
                if (CompiledFunction::detect_issues(dim_expr)) {
                    return nullptr;
                }
            }
            return peek;
        }
    }
    return nullptr;
}

Node_UP make_dim_expr(const TensorPeek::Dim &src_dim) {
    if (src_dim.second.is_expr()) {
        return NodeTools::copy(*src_dim.second.expr);
    } else {
        return std::make_unique<Number>(as_number(src_dim.second.label));
    }
}

template <typename OP>
Node_UP make_op(Node_UP a, Node_UP b) {
    auto res = std::make_unique<OP>();
    res->bind(std::move(a), std::move(b));
    return res;
}

Node_UP make_floor(Node_UP a) {
    auto res = std::make_unique<Floor>();
    res->bind_next(std::move(a));
    return res;
}

struct PeekAnalyzer {
    SmallVector<size_t> dst_dim_sizes;
    SmallVector<size_t> src_dim_sizes;
    SmallVector<CompiledFunction::UP> src_dim_funs;
    std::shared_ptr<Function const> src_idx_fun;

    struct CellRange {
        size_t offset;
        size_t length;
        bool is_full(size_t num_cells) const {
            return ((offset == 0) && (length == num_cells));
        }
    };

    struct Result {
        bool valid;
        std::optional<CellRange> cell_range;
        static Result simple(CellRange range) { return Result{true, range}; }
        static Result complex() { return Result{true, std::nullopt}; }
        static Result invalid() { return Result{false, std::nullopt}; }
    };

    PeekAnalyzer(const ValueType &dst_type, const ValueType &src_type,
                 const TensorPeek::DimList &dim_list)
    {
        for (const auto& dim: dst_type.dimensions()) {
            dst_dim_sizes.push_back(dim.size);
        }
        for (const auto& dim: src_type.dimensions()) {
            src_dim_sizes.push_back(dim.size);
        }
        Node_UP idx_expr;
        size_t num_params = dst_dim_sizes.size();
        for (size_t i = 0; i < dim_list.size(); ++i) {
            auto dim_expr = make_dim_expr(dim_list[i]);
            src_dim_funs.push_back(std::make_unique<CompiledFunction>(*dim_expr, num_params, PassParams::ARRAY));
            if (i == 0) {
                idx_expr = std::move(dim_expr);
            } else {
                idx_expr = make_floor(std::move(idx_expr));
                idx_expr = make_op<Mul>(std::move(idx_expr), std::make_unique<Number>(src_dim_sizes[i]));
                idx_expr = make_op<Add>(std::move(idx_expr), std::move(dim_expr));
            }
        }
        src_idx_fun = Function::create(std::move(idx_expr), dst_type.dimension_names());
    }

    bool step_params(SmallVector<double> &params) {
        for (size_t idx = params.size(); idx-- > 0; ) {
            if (size_t(params[idx] += 1.0) < dst_dim_sizes[idx]) {
                return true;
            } else {
                params[idx] = 0.0;
            }
        }
        return false;
    }

    size_t calculate_index(const SmallVector<size_t> &src_address) {
        size_t result = 0;
        for (size_t i = 0; i < src_address.size(); ++i) {
            result *= src_dim_sizes[i];
            result += src_address[i];
        }
        return result;
    }

    Result analyze_indexes() {
        CellRange range{0,0};
        bool is_complex = false;
        SmallVector<double> params(dst_dim_sizes.size(), 0.0);
        SmallVector<size_t> src_address(src_dim_sizes.size(), 0);
        do {
            for (size_t i = 0; i < src_dim_funs.size(); ++i) {
                auto dim_fun = src_dim_funs[i]->get_function();
                size_t dim_idx = dim_fun(&params[0]);
                if (dim_idx >= src_dim_sizes[i]) {
                    return Result::invalid();
                }
                src_address[i] = dim_idx;
            }
            size_t idx = calculate_index(src_address);
            if (range.length == 0) {
                range.offset = idx;
            }
            if (idx == (range.offset + range.length)) {
                ++range.length;
            } else {
                is_complex = true;
            }
        } while(step_params(params));
        if (is_complex) {
            return Result::complex();
        }
        return Result::simple(range);
    }
};

} // namespace <unnamed>

const TensorFunction &
DenseLambdaPeekOptimizer::optimize(const TensorFunction &expr, Stash &stash)
{
    if (auto lambda = as<tensor_function::Lambda>(expr)) {
        if (auto peek = find_simple_peek(*lambda)) {
            const ValueType &dst_type = lambda->result_type();
            const ValueType &src_type = lambda->types().get_type(peek->param());
            if (src_type.is_dense()) {
                assert(lambda->bindings().size() == 1);
                assert(src_type.dimensions().size() == peek->dim_list().size());
                size_t param_idx = lambda->bindings()[0];
                PeekAnalyzer analyzer(dst_type, src_type, peek->dim_list());
                auto result = analyzer.analyze_indexes();
                if (result.valid) {
                    const auto &get_param = tensor_function::inject(src_type, param_idx, stash);
                    if (result.cell_range && (dst_type.cell_type() == src_type.cell_type())) {
                        auto cell_range = result.cell_range.value();
                        if (cell_range.is_full(src_type.dense_subspace_size())) {
                            return ReplaceTypeFunction::create_compact(dst_type, get_param, stash);
                        } else {
                            return stash.create<DenseCellRangeFunction>(dst_type, get_param,
                                    cell_range.offset, cell_range.length);
                        }
                    } else {
                        return stash.create<DenseLambdaPeekFunction>(dst_type, get_param,
                                std::move(analyzer.src_idx_fun));
                    }
                }
            }
        }
    }
    return expr;
}

} // namespace
