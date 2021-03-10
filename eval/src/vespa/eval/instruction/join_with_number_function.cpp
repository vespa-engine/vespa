// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "join_with_number_function.h"
#include <vespa/vespalib/objects/objectvisitor.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/inline_operation.h>
#include <vespa/vespalib/util/typify.h>

using namespace vespalib::eval::tensor_function;
using namespace vespalib::eval::operation;

namespace vespalib::eval {

using Instruction = InterpretedFunction::Instruction;
using State = InterpretedFunction::State;

namespace {

template <typename CT, bool inplace>
ArrayRef<CT> make_dst_cells(ConstArrayRef<CT> src_cells, Stash &stash) {
    if (inplace) {
        return unconstify(src_cells);
    } else {
        return stash.create_uninitialized_array<CT>(src_cells.size());
    }
}

template <typename CT, typename Fun, bool inplace, bool swap>
void my_number_join_op(State &state, uint64_t param) {
    using OP = typename std::conditional<swap,SwapArgs2<Fun>,Fun>::type;
    OP my_op((join_fun_t)param);
    const Value &tensor = state.peek(swap ? 0 : 1);
    CT number = state.peek(swap ? 1 : 0).as_double();
    auto src_cells = tensor.cells().typify<CT>();
    auto dst_cells = make_dst_cells<CT, inplace>(src_cells, state.stash);
    apply_op2_vec_num(dst_cells.begin(), src_cells.begin(), number, dst_cells.size(), my_op);
    if (inplace) {
        state.pop_pop_push(tensor);
    } else {
        state.pop_pop_push(state.stash.create<ValueView>(tensor.type(), tensor.index(), TypedCells(dst_cells)));
    }
}

struct SelectJoinWithNumberOp {
    template<typename CT, typename Fun,
             typename InputIsMutable, typename NumberWasLeft>
    static auto invoke() {
        return my_number_join_op<CT, Fun, InputIsMutable::value, NumberWasLeft::value>;
    }
};

} // namespace <unnamed>

JoinWithNumberFunction::JoinWithNumberFunction(const Join &original, bool tensor_was_right)
    : tensor_function::Op2(original.result_type(), original.lhs(), original.rhs()),
      _primary(tensor_was_right ? Primary::RHS : Primary::LHS),
      _function(original.function())
{
}

JoinWithNumberFunction::~JoinWithNumberFunction() = default;

bool
JoinWithNumberFunction::inplace() const {
    if (_primary == Primary::LHS) {
        return lhs().result_is_mutable();
    } else {
        return rhs().result_is_mutable();
    }
}

using MyTypify = TypifyValue<TypifyCellType,vespalib::TypifyBool,operation::TypifyOp2>;

InterpretedFunction::Instruction
JoinWithNumberFunction::compile_self(const ValueBuilderFactory &, Stash &) const
{
    auto op = typify_invoke<4,MyTypify,SelectJoinWithNumberOp>(result_type().cell_type(),
                                                               _function,
                                                               inplace(),
                                                               (_primary == Primary::RHS));
    return Instruction(op, (uint64_t)(_function));
}

void
JoinWithNumberFunction::visit_self(vespalib::ObjectVisitor &visitor) const
{
    Super::visit_self(visitor);
    visitor.visitBool("tensor_was_right", (_primary == Primary::RHS));
    visitor.visitBool("is_inplace", inplace());
}

const TensorFunction &
JoinWithNumberFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    if (! expr.result_type().is_double()) {
        if (const auto *join = as<Join>(expr)) {
            const ValueType &result_type = join->result_type();
            const TensorFunction &lhs = join->lhs();
            const TensorFunction &rhs = join->rhs();
            if (lhs.result_type().is_double() &&
                (result_type == rhs.result_type()))
            {
                return stash.create<JoinWithNumberFunction>(*join, true);
            }
            if (rhs.result_type().is_double() &&
                (result_type == lhs.result_type()))
            {
                return stash.create<JoinWithNumberFunction>(*join, false);
            }
        }
    }
    return expr;
}

} // namespace
