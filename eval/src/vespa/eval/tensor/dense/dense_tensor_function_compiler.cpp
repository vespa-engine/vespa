// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_dot_product_function.h"
#include "dense_tensor_function_compiler.h"
#include <vespa/vespalib/test/insertion_operators.h>
#include <iostream>

using namespace vespalib::eval;
using namespace vespalib::eval::tensor_function;
using namespace vespalib::eval::operation;

namespace vespalib {
namespace tensor {

namespace {

bool
willReduceAllDimensions(const std::vector<vespalib::string> &dimensions)
{
    return (dimensions.empty() || (dimensions.size() == 1));
}

bool
is1dDenseTensor(const ValueType &type)
{
    return (type.is_dense() && (type.dimensions().size() == 1));
}

bool
isCompatibleTensorsForDotProduct(const ValueType &lhsType, const ValueType &rhsType)
{
    return (is1dDenseTensor(lhsType) &&
            is1dDenseTensor(rhsType) &&
            (lhsType.dimensions()[0].name == rhsType.dimensions()[0].name));
}

struct DotProductFunctionCompiler
{
    static TensorFunction::UP compile(Node_UP expr) {
        const Reduce *reduce = as<Reduce>(*expr);
        if (reduce && (reduce->aggr == Aggr::SUM) && willReduceAllDimensions(reduce->dimensions)) {
            const Join *join = as<Join>(*reduce->tensor);
            if (join && (join->function == Mul::f)) {
                const Inject *lhsTensor = as<Inject>(*join->lhs_tensor);
                const Inject *rhsTensor = as<Inject>(*join->rhs_tensor);
                if (lhsTensor && rhsTensor &&
                    isCompatibleTensorsForDotProduct(lhsTensor->result_type, rhsTensor->result_type))
                {
                    return std::make_unique<DenseDotProductFunction>(lhsTensor->tensor_id, rhsTensor->tensor_id);
                }
            }
        }
        return std::move(expr);
    }
};

}

TensorFunction::UP
DenseTensorFunctionCompiler::compile(Node_UP expr)
{
    return DotProductFunctionCompiler::compile(std::move(expr));
}

} // namespace tensor
} // namespace vespalib
