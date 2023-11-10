// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "unpack_bits_function.h"
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/node_tools.h>
#include <vespa/eval/eval/basic_nodes.h>
#include <vespa/eval/eval/operator_nodes.h>
#include <vespa/eval/eval/call_nodes.h>
#include <vespa/eval/eval/tensor_nodes.h>
#include <vespa/eval/eval/wrap_param.h>
#include <cassert>

namespace vespalib::eval {

using namespace vespalib::eval::nodes;
using tensor_function::Lambda;
using tensor_function::MapSubspaces;
using tensor_function::wrap_param;
using tensor_function::unwrap_param;
using tensor_function::inject;

namespace {

//-----------------------------------------------------------------------------

template <typename OCT, bool big>
void my_unpack_bits_op(InterpretedFunction::State &state, uint64_t param) {
    const ValueType &res_type = unwrap_param<ValueType>(param);
    auto packed_cells = state.peek(0).cells().typify<Int8Float>();
    auto unpacked_cells = state.stash.create_uninitialized_array<OCT>(packed_cells.size() * 8);
    OCT *dst = unpacked_cells.begin();
    for (Int8Float cell: packed_cells) {
        if constexpr (big) {
            for (int n = 7; n >= 0; --n) {
                *dst++ = (OCT) bool(cell.get_bits() & (1 << n));
            }
        } else {
            for (int n = 0; n <= 7; ++n) {
                *dst++ = (OCT) bool(cell.get_bits() & (1 << n));
            }
        }
    }
    Value &result_ref = state.stash.create<ValueView>(res_type, state.peek(0).index(), TypedCells(unpacked_cells));
    state.pop_push(result_ref);
}

//-----------------------------------------------------------------------------

struct MyGetFun {
    template <typename OCT, typename BIG> static auto invoke() {
        return my_unpack_bits_op<OCT, BIG::value>;
    }
};

using MyTypify = TypifyValue<TypifyCellType,TypifyBool>;

//-----------------------------------------------------------------------------

bool compatible_types(const ValueType &packed, const ValueType &unpacked) {
    const auto &pdims = packed.dimensions();
    const auto &udims = unpacked.dimensions();
    if ((pdims.size() > 0) &&
        (packed.cell_type() == CellType::INT8) &&
        (packed.is_dense() && unpacked.is_dense()) &&
        (pdims.size() == udims.size()))
    {
        for (size_t i = 0; i < pdims.size() - 1; ++i) {
            if (udims[i].size != pdims[i].size) {
                return false;
            }
        }
        return udims.back().size == (pdims.back().size * 8);
    }
    return false;
}

bool is_little_bit_expr(const Node &node, size_t wanted_param) {
    // 'x%8'
    if (auto mod = as<Mod>(node)) {
        if (auto param = as<Symbol>(mod->lhs())) {
            if (auto eight = as<Number>(mod->rhs())) {
                return ((param->id() == wanted_param) && (eight->value() == 8.0));
            }
        }
    }
    return false;
}

bool is_big_bit_expr(const Node &node, size_t wanted_param) {
    // '7-(x%8)'
    if (auto sub = as<Sub>(node)) {
        if (auto seven = as<Number>(sub->lhs())) {
            return ((seven->value() == 7.0) && is_little_bit_expr(sub->rhs(), wanted_param));
        }
    }
    return false;
}

bool is_ident_expr(const Node &node, size_t wanted_param) {
    // 'x'
    if (auto param = as<Symbol>(node)) {
        return (param->id() == wanted_param);
    }
    return false;
}

bool is_byte_expr(const Node &node, size_t wanted_param) {
    // 'x/8'
    if (auto div = as<Div>(node)) {
        if (auto param = as<Symbol>(div->lhs())) {
            if (auto eight = as<Number>(div->rhs())) {
                return ((param->id() == wanted_param) && (eight->value() == 8.0));
            }
        }
    }
    return false;
}

bool is_byte_peek(const TensorPeek &peek, size_t dim_cnt) {
    if (auto param = as<Symbol>(peek.param())) {
        if ((dim_cnt > 0) &&
            (param->id() == dim_cnt) &&
            (peek.dim_list().size() == dim_cnt) &&
            (peek.num_children() == (dim_cnt + 1)))
        {
            for (size_t i = 0; i < dim_cnt - 1; ++i) {
                if (!is_ident_expr(peek.get_child(i + 1), i)) {
                    return false;
                }
            }
            return is_byte_expr(peek.get_child(dim_cnt), dim_cnt - 1);
        }
    }
    return false;
}

//-----------------------------------------------------------------------------

struct Result {
    const bool is_unpack_bits;
    const bool is_big_bitorder;
    const ValueType &src_type;
};

Result detect_unpack_bits(const ValueType &dst_type,
                          size_t num_bindings,
                          const Function &lambda,
                          const NodeTypes &types)
{
    size_t dim_cnt = dst_type.count_indexed_dimensions();
    if ((num_bindings == 1) && (lambda.num_params() == (dim_cnt + 1))) {
        if (auto bit = as<Bit>(lambda.root())) {
            if (auto peek = as<TensorPeek>(bit->get_child(0))) {
                const ValueType &src_type = types.get_type(peek->param());
                if (compatible_types(src_type, dst_type) && is_byte_peek(*peek, dim_cnt)) {
                    assert(dim_cnt > 0);
                    if (is_big_bit_expr(bit->get_child(1), dim_cnt - 1)) {
                        return {true, true, src_type};
                    } else if (is_little_bit_expr(bit->get_child(1), dim_cnt - 1)) {
                        return {true, false, src_type};
                    }
                }
            }
        }
    }
    return {false, false, dst_type};
}

} // namespace <unnamed>

UnpackBitsFunction::UnpackBitsFunction(const ValueType &res_type_in,
                                       const TensorFunction &packed,
                                       bool big_bitorder)
  : Op1(res_type_in, packed),
    _big_bitorder(big_bitorder)
{
}

InterpretedFunction::Instruction
UnpackBitsFunction::compile_self(const ValueBuilderFactory &, Stash &) const
{
    const ValueType &res_type = result_type();
    auto op = typify_invoke<2,MyTypify,MyGetFun>(res_type.cell_type(), _big_bitorder);
    return InterpretedFunction::Instruction(op, wrap_param<ValueType>(res_type));
}

const TensorFunction &
UnpackBitsFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    if (auto lambda = as<Lambda>(expr)) {
        auto result = detect_unpack_bits(lambda->result_type(), lambda->bindings().size(), lambda->lambda(), lambda->types());
        if (result.is_unpack_bits) {
            assert(lambda->bindings().size() == 1);
            const TensorFunction &input = inject(result.src_type, lambda->bindings()[0], stash);
            return stash.create<UnpackBitsFunction>(lambda->result_type(), input, result.is_big_bitorder);
        }
    }
    if (auto map_subspaces = as<MapSubspaces>(expr)) {
        if (auto lambda = as<TensorLambda>(map_subspaces->lambda().root())) {
            auto result = detect_unpack_bits(lambda->type(), lambda->bindings().size(), lambda->lambda(), map_subspaces->types());
            if (result.is_unpack_bits) {
                return stash.create<UnpackBitsFunction>(map_subspaces->result_type(), map_subspaces->child(), result.is_big_bitorder);
            }
        }
    }
    return expr;
}

} // namespace
