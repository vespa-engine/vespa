// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>
#include "dense_tensor_view.h"
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>

namespace vespalib::tensor {

using XWInput = DenseTensorView::CellsRef;
using XWOutput = ArrayRef<double>;

/**
 * Tensor function for product of one 1-dimensional and one 2-dimensional dense tensor.
 */
class DenseXWProductFunction : public eval::tensor_function::Op2
{
    using Super = eval::tensor_function::Op2;
public:
    struct Self {
        const eval::ValueType _resultType;
        const size_t _vectorSize;
        const size_t _resultSize;
        hwaccelrated::IAccelrated::UP _hwAccelerator;
        Self(const eval::ValueType &resultType,
             size_t vectorSize,
             size_t resultSize);
        ~Self() {}
    };

private:
    const size_t _vectorSize;
    const size_t _resultSize;
    bool _commonDimensionInnermost;

public:
    DenseXWProductFunction(const eval::ValueType &resultType,
                           const eval::TensorFunction &vector_in,
                           const eval::TensorFunction &matrix_in,
                           size_t vectorSize,
                           size_t resultSize,
                           bool matrixHasCommonDimensionInnermost);

    ~DenseXWProductFunction() {}

    bool result_is_mutable() const override { return true; }

    size_t vectorSize() const { return _vectorSize; }
    size_t resultSize() const { return _resultSize; }

    bool matrixHasCommonDimensionInnermost() const { return _commonDimensionInnermost; }

    eval::InterpretedFunction::Instruction compile_self(Stash &stash) const override;
    void visit_self(vespalib::ObjectVisitor &visitor) const override;
    static const eval::TensorFunction &optimize(const eval::TensorFunction &expr, Stash &stash);
};

} // namespace vespalib::tensor

