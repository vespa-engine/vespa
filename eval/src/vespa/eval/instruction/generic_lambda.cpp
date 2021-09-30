// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generic_lambda.h"
#include <vespa/eval/eval/llvm/compiled_function.h>
#include <vespa/eval/eval/llvm/compile_cache.h>
#include <assert.h>

using namespace vespalib::eval::tensor_function;

namespace vespalib::eval::instruction {

using Instruction = InterpretedFunction::Instruction;
using State = InterpretedFunction::State;

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
    const SmallVector<double> &labels;
    const LazyParams          &params;
    const std::vector<size_t> &bindings;
    ParamProxy(const SmallVector<double> &labels_in, const LazyParams &params_in, const std::vector<size_t> &bindings_in)
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
void my_compiled_lambda_op(InterpretedFunction::State &state, uint64_t param) {
    const CompiledParams &params = unwrap_param<CompiledParams>(param);
    SmallVector<double> args(params.result_type.dimensions().size() + params.bindings.size(), 0.0);
    double *bind_next = &args[params.result_type.dimensions().size()];
    for (size_t binding: params.bindings) {
        *bind_next++ = state.params->resolve(binding, state.stash).as_double();
    }
    auto fun = params.token->get().get_function();
    ArrayRef<CT> dst_cells = state.stash.create_uninitialized_array<CT>(params.num_cells);
    CT *dst = &dst_cells[0];
    do {
        *dst++ = fun(&args[0]);
    } while (step_labels(&args[0], params.result_type));
    state.stack.push_back(state.stash.create<DenseValueView>(params.result_type, TypedCells(dst_cells)));
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
    InterpretedParams(const Lambda &lambda, const ValueBuilderFactory &factory)
        : result_type(lambda.result_type()),
          bindings(lambda.bindings()),
          num_cells(result_type.dense_subspace_size()),
          fun(factory, lambda.lambda().root(), lambda.types())
    {
        assert(lambda.lambda().num_params() == (result_type.dimensions().size() + bindings.size()));
    }
};

template <typename CT>
void my_interpreted_lambda_op(InterpretedFunction::State &state, uint64_t param) {
    const InterpretedParams &params = unwrap_param<InterpretedParams>(param);
    SmallVector<double> labels(params.result_type.dimensions().size(), 0.0);
    ParamProxy param_proxy(labels, *state.params, params.bindings);
    InterpretedFunction::Context ctx(params.fun);
    ArrayRef<CT> dst_cells = state.stash.create_uninitialized_array<CT>(params.num_cells);
    CT *dst = &dst_cells[0];
    do {
        *dst++ = params.fun.eval(ctx, param_proxy).as_double();
    } while (step_labels(&labels[0], params.result_type));
    state.stack.push_back(state.stash.create<DenseValueView>(params.result_type, TypedCells(dst_cells)));
}

struct MyInterpretedLambdaOp {
    template <typename CT>
    static auto invoke() { return my_interpreted_lambda_op<CT>; }
};

//-----------------------------------------------------------------------------

} // namespace <unnamed>

Instruction
GenericLambda::make_instruction(const tensor_function::Lambda &lambda_in,
                                const ValueBuilderFactory &factory, Stash &stash)
{
    const ValueType & result_type = lambda_in.result_type();
    assert(result_type.count_mapped_dimensions() == 0);
    if (!CompiledFunction::detect_issues(lambda_in.lambda()) &&
        lambda_in.types().all_types_are_double())
    {
        // can do compiled version
        CompiledParams &params = stash.create<CompiledParams>(lambda_in);
        auto op = typify_invoke<1,TypifyCellType,MyCompiledLambdaOp>(result_type.cell_type());
        return Instruction(op, wrap_param<CompiledParams>(params));
    } else {
        InterpretedParams &params = stash.create<InterpretedParams>(lambda_in, factory);
        auto op = typify_invoke<1,TypifyCellType,MyInterpretedLambdaOp>(result_type.cell_type());
        return Instruction(op, wrap_param<InterpretedParams>(params));
    }
}

} // namespace
