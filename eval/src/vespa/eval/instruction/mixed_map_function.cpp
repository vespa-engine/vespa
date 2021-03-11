// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mixed_map_function.h"
#include <vespa/vespalib/util/typify.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/inline_operation.h>

namespace vespalib::eval {

using operation::TypifyOp1;
using tensor_function::map_fun_t;

using Instruction = InterpretedFunction::Instruction;
using State = InterpretedFunction::State;

namespace {

template <typename CT, typename Fun>
void my_inplace_map_op(State &state, uint64_t param) {
    Fun my_fun((map_fun_t)param);
    auto const &child = state.peek(0);
    auto src_cells = child.cells().typify<CT>();
    auto dst_cells = unconstify(src_cells);
    apply_op1_vec(dst_cells.begin(), src_cells.begin(), dst_cells.size(), my_fun);
}

//-----------------------------------------------------------------------------

struct MyGetFun {
    template <typename ICM, typename Fun> static auto invoke() {
        using ICT = CellValueType<ICM::value.cell_type>;
        using OCT = CellValueType<ICM::value.map().cell_type>;
        assert((std::is_same_v<ICT,OCT>));
        return my_inplace_map_op<OCT, Fun>;
    }
};

using MyTypify = TypifyValue<TypifyCellMeta,TypifyOp1>;

} // namespace vespalib::eval::<unnamed>

//-----------------------------------------------------------------------------

MixedMapFunction::MixedMapFunction(const ValueType &result_type,
                                   const TensorFunction &child,
                                   map_fun_t function_in)
    : Map(result_type, child, function_in)
{
}

MixedMapFunction::~MixedMapFunction() = default;

Instruction
MixedMapFunction::compile_self(const ValueBuilderFactory &, Stash &) const
{
    auto input_cell_meta = child().result_type().cell_meta().limit().not_scalar();
    auto op = typify_invoke<2,MyTypify,MyGetFun>(input_cell_meta, function());
    static_assert(sizeof(uint64_t) == sizeof(function()));
    return Instruction(op, (uint64_t)(function()));
}

const TensorFunction &
MixedMapFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    if (auto map = as<Map>(expr)) {
        if ((map->result_type() == map->child().result_type())
            && (! map->child().result_type().is_double())
            && map->child().result_is_mutable())
        {
            return stash.create<MixedMapFunction>(map->result_type(), map->child(), map->function());
        }
    }
    return expr;
}

} // namespace vespalib::eval
