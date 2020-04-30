// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_lambda_peek_function.h"
#include "dense_tensor_view.h"
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/llvm/compile_cache.h>

namespace vespalib::tensor {

using eval::CompileCache;
using eval::Function;
using eval::InterpretedFunction;
using eval::PassParams;
using eval::TensorEngine;
using eval::TensorFunction;
using eval::Value;
using eval::ValueType;
using eval::as;
using namespace eval::tensor_function;

namespace {

struct Self {
    const ValueType &result_type;
    CompileCache::Token::UP compile_token;
    Self(const ValueType &result_type_in, const Function &function)
        : result_type(result_type_in),
          compile_token(CompileCache::compile(function, PassParams::ARRAY)) {}
};

bool step_params(std::vector<double> &params, const ValueType &type) {
    const auto &dims = type.dimensions();
    for (size_t idx = params.size(); idx-- > 0; ) {
        if (size_t(params[idx] += 1.0) < dims[idx].size) {
            return true;
        } else {
            params[idx] = 0.0;
        }
    }
    return false;
}

template <typename DST_CT, typename SRC_CT>
void my_lambda_peek_op(InterpretedFunction::State &state, uint64_t param) {
    const auto *self = (const Self *)(param);
    auto src_cells = DenseTensorView::typify_cells<SRC_CT>(state.peek(0));
    ArrayRef<DST_CT> dst_cells = state.stash.create_array<DST_CT>(self->result_type.dense_subspace_size());
    DST_CT *dst = &dst_cells[0];
    std::vector<double> params(self->result_type.dimensions().size(), 0.0);
    auto idx_fun = self->compile_token->get().get_function();
    do {
        *dst++ = src_cells[size_t(idx_fun(&params[0]))];
    } while(step_params(params, self->result_type));
    state.pop_push(state.stash.create<DenseTensorView>(self->result_type, TypedCells(dst_cells)));
}

struct MyLambdaPeekOp {
    template <typename DST_CT, typename SRC_CT>
    static auto get_fun() { return my_lambda_peek_op<DST_CT, SRC_CT>; }
};

} // namespace vespalib::tensor::<unnamed>

DenseLambdaPeekFunction::DenseLambdaPeekFunction(const ValueType &result_type,
                                                 const TensorFunction &child,
                                                 std::shared_ptr<Function const> idx_fun)
    : Op1(result_type, child),
      _idx_fun(std::move(idx_fun))
{
}

DenseLambdaPeekFunction::~DenseLambdaPeekFunction() = default;

InterpretedFunction::Instruction
DenseLambdaPeekFunction::compile_self(const TensorEngine &, Stash &stash) const
{
    const Self &self = stash.create<Self>(result_type(), *_idx_fun);
    auto op = select_2<MyLambdaPeekOp>(result_type().cell_type(), child().result_type().cell_type());
    static_assert(sizeof(uint64_t) == sizeof(&self));
    assert(child().result_type().is_dense());
    return InterpretedFunction::Instruction(op, (uint64_t)&self);
}

vespalib::string
DenseLambdaPeekFunction::idx_fun_dump() const {
    return _idx_fun->dump_as_lambda();
}

} // namespace vespalib::tensor
