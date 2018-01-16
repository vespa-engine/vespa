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
class DenseXWProductFunction : public eval::TensorFunction
{
private:
    const eval::ValueType _resultType;
    const size_t _vectorId;
    const size_t _matrixId;
    const size_t _vectorSize;
    const size_t _resultSize;
    bool _commonDimensionInnermost;
    hwaccelrated::IAccelrated::UP _hwAccelerator;

    void multiDotProduct(const XWInput &v, const XWInput &m, XWOutput &r) const;
    void transposedProduct(const XWInput &v, const XWInput &m, XWOutput &r) const;
public:
    DenseXWProductFunction(const eval::ValueType &resultType,
                           size_t vectorId,
                           size_t matrixId,
                           size_t vectorSize,
                           size_t resultSize,
                           bool matrixHasCommonDimensionInnermost);

    ~DenseXWProductFunction() {}

    size_t vectorId() const { return _vectorId; }
    size_t matrixId() const { return _matrixId; }

    size_t vectorSize() const { return _vectorSize; }
    size_t resultSize() const { return _resultSize; }

    bool matrixHasCommonDimensionInnermost() const { return _commonDimensionInnermost; }

    const eval::ValueType &result_type() const override { return _resultType; }
    void push_children(std::vector<Child::CREF> &) const override {}
    const eval::Value &eval(const eval::LazyParams &params, Stash &stash) const override;
};

}

