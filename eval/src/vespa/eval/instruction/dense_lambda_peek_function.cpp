// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_lambda_peek_function.h"
#include "index_lookup_table.h"
#include <vespa/eval/eval/value.h>

namespace vespalib::eval {

using namespace tensor_function;

namespace {

struct Self {
    const ValueType &result_type;
    IndexLookupTable::Token::UP table_token;
    Self(const ValueType &result_type_in, const Function &function)
        : result_type(result_type_in),
          table_token(IndexLookupTable::create(function, result_type_in))
    {
        assert(table_token->get().size() == result_type.dense_subspace_size());
    }
};

template <typename DST_CT, typename SRC_CT>
void my_lambda_peek_op(InterpretedFunction::State &state, uint64_t param) {
    const auto &self = unwrap_param<Self>(param);
    const std::vector<uint32_t> &lookup_table = self.table_token->get();
    auto src_cells = state.peek(0).cells().typify<SRC_CT>();
    ArrayRef<DST_CT> dst_cells = state.stash.create_uninitialized_array<DST_CT>(lookup_table.size());
    DST_CT *dst = &dst_cells[0];
    for (uint32_t idx: lookup_table) {
        *dst++ = src_cells[idx];
    }
    state.pop_push(state.stash.create<DenseValueView>(self.result_type, TypedCells(dst_cells)));
}

struct MyLambdaPeekOp {
    template <typename DST_CT, typename SRC_CT>
    static auto invoke() { return my_lambda_peek_op<DST_CT, SRC_CT>; }
};

} // namespace <unnamed>

DenseLambdaPeekFunction::DenseLambdaPeekFunction(const ValueType &result_type,
                                                 const TensorFunction &child,
                                                 std::shared_ptr<Function const> idx_fun)
    : Op1(result_type, child),
      _idx_fun(std::move(idx_fun))
{
}

DenseLambdaPeekFunction::~DenseLambdaPeekFunction() = default;

InterpretedFunction::Instruction
DenseLambdaPeekFunction::compile_self(const ValueBuilderFactory &, Stash &stash) const
{
    const Self &self = stash.create<Self>(result_type(), *_idx_fun);
    using MyTypify = TypifyCellType;
    auto op = typify_invoke<2,MyTypify,MyLambdaPeekOp>(result_type().cell_type(), child().result_type().cell_type());
    assert(child().result_type().is_dense());
    return InterpretedFunction::Instruction(op, wrap_param<Self>(self));
}

vespalib::string
DenseLambdaPeekFunction::idx_fun_dump() const {
    return _idx_fun->dump_as_lambda();
}

} // namespace
