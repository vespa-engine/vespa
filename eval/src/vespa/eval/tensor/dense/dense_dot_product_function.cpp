// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_dot_product_function.h"
#include "dense_tensor.h"
#include "dense_tensor_view.h"
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/tensor/tensor.h>

namespace vespalib::tensor {

using eval::ValueType;
using eval::TensorFunction;
using eval::as;
using eval::Aggr;
using namespace eval::tensor_function;
using namespace eval::operation;

namespace {

template <typename T>
ConstArrayRef<T> getCellsRef(const eval::Value &value) {
    const DenseTensorView &denseTensor = static_cast<const DenseTensorView &>(value);
    return denseTensor.cellsRef().typify<T>();
}

template <typename LCT, typename RCT>
struct HWSupport {
    static double call(hwaccelrated::IAccelrated *, const ConstArrayRef<LCT> &lhs, const ConstArrayRef<RCT> &rhs) {
        double result = 0.0;
        for (size_t i = 0; i < lhs.size(); ++i) {
            result += (lhs[i] * rhs[i]);
        }
        return result;
    }
};
template <> struct HWSupport<float, float> {
    static double call(hwaccelrated::IAccelrated *hw, const ConstArrayRef<float> &lhs, const ConstArrayRef<float> &rhs) {
        return hw->dotProduct(lhs.cbegin(), rhs.cbegin(), lhs.size());
    }
};
template <> struct HWSupport<double, double> {
    static double call(hwaccelrated::IAccelrated *hw, const ConstArrayRef<double> &lhs, const ConstArrayRef<double> &rhs) {
        return hw->dotProduct(lhs.cbegin(), rhs.cbegin(), lhs.size());
    }
};

template <typename LCT, typename RCT>
void my_dot_product_op(eval::InterpretedFunction::State &state, uint64_t param) {
    auto *hw = (hwaccelrated::IAccelrated *)(param);
    auto lhs = getCellsRef<LCT>(state.peek(1));
    auto rhs = getCellsRef<RCT>(state.peek(0));
    double result = HWSupport<LCT,RCT>::call(hw, lhs, rhs);
    state.pop_pop_push(state.stash.create<eval::DoubleValue>(result));
}

struct MyDotProductOp {
    template <typename LCT, typename RCT>
    static auto get_fun() { return my_dot_product_op<LCT,RCT>; }
};

} // namespace vespalib::tensor::<unnamed>

DenseDotProductFunction::DenseDotProductFunction(const eval::TensorFunction &lhs_in,
                                                 const eval::TensorFunction &rhs_in)
    : eval::tensor_function::Op2(eval::ValueType::double_type(), lhs_in, rhs_in),
      _hwAccelerator(hwaccelrated::IAccelrated::getAccelrator())
{
}

eval::InterpretedFunction::Instruction
DenseDotProductFunction::compile_self(Stash &) const
{
    auto op = select_2<MyDotProductOp>(lhs().result_type().cell_type(),
                                       rhs().result_type().cell_type());
    return eval::InterpretedFunction::Instruction(op, (uint64_t)(_hwAccelerator.get()));
}

bool
DenseDotProductFunction::compatible_types(const ValueType &res, const ValueType &lhs, const ValueType &rhs)
{
    return (res.is_double() && lhs.is_dense() && (rhs.dimensions() == lhs.dimensions()));
}

const TensorFunction &
DenseDotProductFunction::optimize(const eval::TensorFunction &expr, Stash &stash)
{
    auto reduce = as<Reduce>(expr);
    if (reduce && (reduce->aggr() == Aggr::SUM)) {
        auto join = as<Join>(reduce->child());
        if (join && (join->function() == Mul::f)) {
            const TensorFunction &lhs = join->lhs();
            const TensorFunction &rhs = join->rhs();
            if (compatible_types(expr.result_type(), lhs.result_type(), rhs.result_type())) {
                return stash.create<DenseDotProductFunction>(lhs, rhs);
            }
        }
    }
    return expr;
}

} // namespace vespalib::tensor
