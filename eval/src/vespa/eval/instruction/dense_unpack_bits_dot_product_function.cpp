// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_unpack_bits_dot_product_function.h"

#include "dense_dot_product_function.h"
#include "unpack_bits_function.h"

#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/hwaccelerated/functions.h>
#include <vespa/vespalib/objects/objectvisitor.h>

#include <cassert>
#include <cstdlib>

namespace vespalib::eval {

using namespace tensor_function;

namespace {

// 'param' is the bit order: non-zero for big (most significant bit first).
template <typename LCT> void my_bit_dot_product_op(InterpretedFunction::State& state, uint64_t param) {
    auto   lhs_cells = state.peek(1).cells().typify<LCT>();
    auto   packed_cells = state.peek(0).cells().typify<Int8Float>();
    // Int8Float is a plain wrapper around a single int8_t
    static_assert(sizeof(Int8Float) == sizeof(int8_t));
    static_assert(alignof(Int8Float) == alignof(int8_t));
    static_assert(std::has_unique_object_representations_v<Int8Float>);
    const auto* packed = reinterpret_cast<const int8_t*>(packed_cells.data());
    double      result = hwaccelerated::bit_dot_product(lhs_cells.data(), packed, lhs_cells.size(), param != 0);
    state.pop_pop_push(state.stash.create<DoubleValue>(result));
}

bool is_plain_dense_operand(const ValueType& type) {
    return type.is_dense() && (type.count_indexed_dimensions() == 1) &&
           ((type.cell_type() == CellType::FLOAT) || (type.cell_type() == CellType::DOUBLE) ||
            (type.cell_type() == CellType::BFLOAT16));
}

} // namespace

DenseUnpackBitsDotProductFunction::DenseUnpackBitsDotProductFunction(const TensorFunction& lhs_in,
                                                                      const TensorFunction& packed_in,
                                                                      bool                  big_bitorder)
    : tensor_function::Op2(ValueType::double_type(), lhs_in, packed_in), _big_bitorder(big_bitorder) {
}

InterpretedFunction::Instruction DenseUnpackBitsDotProductFunction::compile_self(const ValueBuilderFactory&,
                                                                                 Stash&) const {
    auto                             lct = lhs().result_type().cell_type();
    InterpretedFunction::op_function op = nullptr;
    switch (lct) {
    case CellType::FLOAT:
        op = my_bit_dot_product_op<float>;
        break;
    case CellType::DOUBLE:
        op = my_bit_dot_product_op<double>;
        break;
    case CellType::BFLOAT16:
        op = my_bit_dot_product_op<BFloat16>;
        break;
    default:
        abort(); // is_plain_dense_operand() guarantees one of the above
    }
    return InterpretedFunction::Instruction(op, _big_bitorder ? 1 : 0);
}

void DenseUnpackBitsDotProductFunction::visit_self(vespalib::ObjectVisitor& visitor) const {
    Super::visit_self(visitor);
    visitor.visitBool("big_bitorder", _big_bitorder);
}

const TensorFunction& DenseUnpackBitsDotProductFunction::optimize(const TensorFunction& expr, Stash& stash) {
    auto dot = as<DenseDotProductFunction>(expr);
    if (!dot) {
        return expr;
    }
    // Exactly one side must be an unpack_bits; the other side is then the
    // plain dense vector.
    auto lhs_unpack = as<UnpackBitsFunction>(dot->lhs());
    auto rhs_unpack = as<UnpackBitsFunction>(dot->rhs());
    if ((lhs_unpack != nullptr) == (rhs_unpack != nullptr)) {
        return expr;
    }
    const UnpackBitsFunction& unpack = lhs_unpack ? *lhs_unpack : *rhs_unpack;
    const TensorFunction&     plain = lhs_unpack ? dot->rhs() : dot->lhs();
    const ValueType&          packed_type = unpack.child().result_type();
    if (!is_plain_dense_operand(plain.result_type()) || !packed_type.is_dense() ||
        (packed_type.cell_type() != CellType::INT8))
    {
        return expr;
    }
    return stash.create<DenseUnpackBitsDotProductFunction>(plain, unpack.child(), unpack.big_bitorder());
}

} // namespace vespalib::eval
