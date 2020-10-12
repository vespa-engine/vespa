// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_lambda_function.h"
#include "dense_tensor_view.h"
#include <vespa/vespalib/objects/objectvisitor.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/eval/llvm/compiled_function.h>
#include <vespa/eval/eval/llvm/compile_cache.h>
#include <assert.h>

namespace vespalib::tensor {

using eval::CompileCache;
using eval::CompiledFunction;
using eval::InterpretedFunction;
using eval::LazyParams;
using eval::PassParams;
using eval::TensorEngine;
using eval::TensorFunction;
using eval::Value;
using eval::DoubleValue;
using eval::ValueType;
using eval::as;
using vespalib::Stash;

using Instruction = InterpretedFunction::Instruction;
using State = InterpretedFunction::State;

using namespace eval::tensor_function;

const TensorEngine &prod_engine = DefaultTensorEngine::ref();

namespace {

//-----------------------------------------------------------------------------

bool step_labels(double *labels, const ValueType &type) {
    for (size_t idx = type.dimensions().size(); idx-- > 0; ) {
        if ((labels[idx] += 1.0) < type.dimensions()[idx].size) {
            return true;
        } else {
            labels[idx] = 0.0;
        }
    }
    return false;
}

struct ParamProxy : public LazyParams {
    const std::vector<double> &labels;
    const LazyParams          &params;
    const std::vector<size_t> &bindings;
    ParamProxy(const std::vector<double> &labels_in, const LazyParams &params_in, const std::vector<size_t> &bindings_in)
        : labels(labels_in), params(params_in), bindings(bindings_in) {}
    const Value &resolve(size_t idx, Stash &stash) const override {
        if (idx < labels.size()) {
            return stash.create<DoubleValue>(labels[idx]);
        }
        return params.resolve(bindings[idx - labels.size()], stash);
    }
};

//-----------------------------------------------------------------------------

struct CompiledParams {
    const ValueType &result_type;
    const std::vector<size_t> &bindings;
    size_t num_cells;
    CompileCache::Token::UP token;
    CompiledParams(const Lambda &lambda) 
        : result_type(lambda.result_type()),
          bindings(lambda.bindings()),
          num_cells(result_type.dense_subspace_size()),
          token(CompileCache::compile(lambda.lambda(), PassParams::ARRAY))
    {
        assert(lambda.lambda().num_params() == (result_type.dimensions().size() + bindings.size()));
    }
};

template <typename CT>
void my_compiled_lambda_op(eval::InterpretedFunction::State &state, uint64_t param) {
    const CompiledParams &params = unwrap_param<CompiledParams>(param);
    std::vector<double> args(params.result_type.dimensions().size() + params.bindings.size(), 0.0);
    double *bind_next = &args[params.result_type.dimensions().size()];
    for (size_t binding: params.bindings) {
        *bind_next++ = state.params->resolve(binding, state.stash).as_double();
    }
    auto fun = params.token->get().get_function();
    ArrayRef<CT> dst_cells = state.stash.create_array<CT>(params.num_cells);
    CT *dst = &dst_cells[0];
    do {
        *dst++ = fun(&args[0]);
    } while (step_labels(&args[0], params.result_type));
    state.stack.push_back(state.stash.create<DenseTensorView>(params.result_type, TypedCells(dst_cells)));
}

struct MyCompiledLambdaOp {
    template <typename CT>
    static auto invoke() { return my_compiled_lambda_op<CT>; }
};

//-----------------------------------------------------------------------------

struct InterpretedParams {
    const ValueType &result_type;
    const std::vector<size_t> &bindings;
    size_t num_cells;
    InterpretedFunction fun;
    InterpretedParams(const Lambda &lambda)
        : result_type(lambda.result_type()),
          bindings(lambda.bindings()),
          num_cells(result_type.dense_subspace_size()),
          fun(prod_engine, lambda.lambda().root(), lambda.types())
    {
        assert(lambda.lambda().num_params() == (result_type.dimensions().size() + bindings.size()));
    }
};

template <typename CT>
void my_interpreted_lambda_op(eval::InterpretedFunction::State &state, uint64_t param) {
    const InterpretedParams &params = unwrap_param<InterpretedParams>(param);
    std::vector<double> labels(params.result_type.dimensions().size(), 0.0);
    ParamProxy param_proxy(labels, *state.params, params.bindings);
    InterpretedFunction::Context ctx(params.fun);
    ArrayRef<CT> dst_cells = state.stash.create_array<CT>(params.num_cells);
    CT *dst = &dst_cells[0];
    do {
        *dst++ = params.fun.eval(ctx, param_proxy).as_double();
    } while (step_labels(&labels[0], params.result_type));
    state.stack.push_back(state.stash.create<DenseTensorView>(params.result_type, TypedCells(dst_cells)));
}

struct MyInterpretedLambdaOp {
    template <typename CT>
    static auto invoke() { return my_interpreted_lambda_op<CT>; }
};

//-----------------------------------------------------------------------------

}

DenseLambdaFunction::DenseLambdaFunction(const Lambda &lambda_in)
    : Super(lambda_in.result_type()),
      _lambda(lambda_in)
{
}

DenseLambdaFunction::~DenseLambdaFunction() = default;

DenseLambdaFunction::EvalMode
DenseLambdaFunction::eval_mode() const
{
    if (!CompiledFunction::detect_issues(_lambda.lambda()) &&
        _lambda.types().all_types_are_double())
    {
        return EvalMode::COMPILED;
    } else {
        return EvalMode::INTERPRETED;
    }
}

Instruction
DenseLambdaFunction::compile_self(const TensorEngine &engine, Stash &stash) const
{
    assert(&engine == &prod_engine);
    auto mode = eval_mode();
    using MyTypify = eval::TypifyCellType;
    if (mode == EvalMode::COMPILED) {
        CompiledParams &params = stash.create<CompiledParams>(_lambda);
        auto op = typify_invoke<1,MyTypify,MyCompiledLambdaOp>(result_type().cell_type());
        static_assert(sizeof(&params) == sizeof(uint64_t));
        return Instruction(op, (uint64_t)(&params));
    } else {
        assert(mode == EvalMode::INTERPRETED);
        InterpretedParams &params = stash.create<InterpretedParams>(_lambda);
        auto op = typify_invoke<1,MyTypify,MyInterpretedLambdaOp>(result_type().cell_type());
        static_assert(sizeof(&params) == sizeof(uint64_t));
        return Instruction(op, (uint64_t)(&params));
    }
}

const eval::TensorFunction &
DenseLambdaFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    if (auto lambda = as<Lambda>(expr)) {
        return stash.create<DenseLambdaFunction>(*lambda);
    }
    return expr;
}

}
