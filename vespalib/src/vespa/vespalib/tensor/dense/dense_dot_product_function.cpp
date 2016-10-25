// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "dense_dot_product_function.h"
#include "dense_tensor.h"
#include "dense_tensor_view.h"
#include <vespa/vespalib/eval/value.h>
#include <vespa/vespalib/tensor/tensor.h>

namespace vespalib {
namespace tensor {

using CellsRef = DenseTensorView::CellsRef;

DenseDotProductFunction::DenseDotProductFunction(size_t lhsTensorId_, size_t rhsTensorId_)
    : _lhsTensorId(lhsTensorId_),
      _rhsTensorId(rhsTensorId_),
      _hwAccelerator(hwaccelrated::IAccelrated::getAccelrator())
{
}

namespace {

CellsRef
getCellsRef(const eval::Value &value)
{
    assert(value.is_tensor());
    const Tensor *tensor = dynamic_cast<const Tensor *>(value.as_tensor());
    assert(tensor);
    const DenseTensorView *denseTensorView = dynamic_cast<const DenseTensorView *>(tensor);
    if (denseTensorView) {
        return denseTensorView->cells();
    } else {
        // TODO: Make DenseTensor inherit DenseTensorView
        const DenseTensor *denseTensor = dynamic_cast<const DenseTensor *>(tensor);
        assert(denseTensor);
        return CellsRef(&denseTensor->cells()[0],
                        denseTensor->cells().size());
    }
}

}

const eval::Value &
DenseDotProductFunction::eval(const Input &input, Stash &stash) const
{
    DenseTensorView::CellsRef lhsCells = getCellsRef(input.get_tensor(_lhsTensorId));
    DenseTensorView::CellsRef rhsCells = getCellsRef(input.get_tensor(_rhsTensorId));
    size_t numCells = std::min(lhsCells.size(), rhsCells.size());
    double result = _hwAccelerator->dotProduct(lhsCells.cbegin(), rhsCells.cbegin(), numCells);
    return stash.create<eval::DoubleValue>(result);
}

} // namespace tensor
} // namespace vespalib
