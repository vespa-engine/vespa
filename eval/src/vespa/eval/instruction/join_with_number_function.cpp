// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
using vespalib::eval::tensor_function::unwrap_param;
using vespalib::eval::tensor_function::wrap_param;

namespace {

struct JoinWithNumberParam {
    const ValueType res_type;
    const join_fun_t function;
    JoinWithNumberParam(const ValueType &r, join_fun_t f) : res_type(r), function(f) {}
};

template <typename ICT, typename OCT, bool inplace>
ArrayRef<OCT> make_dst_cells(ConstArrayRef<ICT> src_cells, Stash &stash) {
    if constexpr (inplace) {
        static_assert(std::is_same_v<ICT,OCT>);
        return unconstify(src_cells);
    } else {
        return stash.create_uninitialized_array<OCT>(src_cells.size());
    }
}

template <typename ICT, typename OCT, typename Fun, bool inplace, bool swap>
void my_number_join_op(State &state, uint64_t param_in) {
    const auto &param = unwrap_param<JoinWithNumberParam>(param_in);
    using OP = typename std::conditional<swap,SwapArgs2<Fun>,Fun>::type;
    OP my_op(param.function);
    const Value &tensor = state.peek(swap ? 0 : 1);
    OCT number = state.peek(swap ? 1 : 0).as_double();
    auto src_cells = tensor.cells().typify<ICT>();
    auto dst_cells = make_dst_cells<ICT, OCT, inplace>(src_cells, state.stash);
    apply_op2_vec_num(dst_cells.begin(), src_cells.begin(), number, dst_cells.size(), my_op);
    if (inplace) {
        state.pop_pop_push(tensor);
    } else {
        state.pop_pop_push(state.stash.create<ValueView>(param.res_type, tensor.index(), TypedCells(dst_cells)));
    }
}

struct SelectJoinWithNumberOp {
    template<typename CM, typename Fun,
             typename PrimaryMutable, typename NumberWasLeft>
    static auto invoke() {
        constexpr CellMeta icm = CM::value;
        constexpr CellMeta num(CellType::DOUBLE, true);
        constexpr CellMeta ocm = CellMeta::join(icm, num); 
        using ICT = CellValueType<icm.cell_type>;
        using OCT = CellValueType<ocm.cell_type>;
        constexpr bool inplace = (PrimaryMutable::value && std::is_same_v<ICT,OCT>);
        return my_number_join_op<ICT, OCT, Fun, inplace, NumberWasLeft::value>;
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
JoinWithNumberFunction::primary_is_mutable() const {
    if (_primary == Primary::LHS) {
        return lhs().result_is_mutable();
    } else {
        return rhs().result_is_mutable();
    }
}

using MyTypify = TypifyValue<TypifyCellMeta,vespalib::TypifyBool,operation::TypifyOp2>;

InterpretedFunction::Instruction
JoinWithNumberFunction::compile_self(const ValueBuilderFactory &, Stash &stash) const
{
    const auto &param = stash.create<JoinWithNumberParam>(result_type(), _function);
    auto input_type = (_primary == Primary::LHS) ? lhs().result_type() : rhs().result_type();
    assert(result_type() == ValueType::join(input_type, ValueType::double_type()));
    auto op = typify_invoke<4,MyTypify,SelectJoinWithNumberOp>(input_type.cell_meta(),
                                                               _function,
                                                               primary_is_mutable(),
                                                               (_primary == Primary::RHS));
    return Instruction(op, wrap_param<JoinWithNumberParam>(param));
}

void
JoinWithNumberFunction::visit_self(vespalib::ObjectVisitor &visitor) const
{
    Super::visit_self(visitor);
    visitor.visitBool("tensor_was_right", (_primary == Primary::RHS));
    visitor.visitBool("primary_is_mutable", primary_is_mutable());
}

const TensorFunction &
JoinWithNumberFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    if (! expr.result_type().is_double()) {
        if (const auto *join = as<Join>(expr)) {
            const TensorFunction &lhs = join->lhs();
            const TensorFunction &rhs = join->rhs();
            if (lhs.result_type().is_double()) {
                return stash.create<JoinWithNumberFunction>(*join, true);
            }
            if (rhs.result_type().is_double()) {
                return stash.create<JoinWithNumberFunction>(*join, false);
            }
        }
    }
    return expr;
}

} // namespace
