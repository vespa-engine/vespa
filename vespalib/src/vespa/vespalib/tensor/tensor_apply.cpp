// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "tensor_apply.h"

namespace vespalib {
namespace tensor {

template <class TensorT>
TensorApply<TensorT>::TensorApply(const TensorImplType &tensor,
                                  const CellFunction &func)
    : Parent(tensor.dimensions())
{
    for (const auto &cell : tensor.cells()) {
        _builder.insertCell(cell.first, func.apply(cell.second));
    }
}

template class TensorApply<CompactTensor>;
template class TensorApply<CompactTensorV2>;

} // namespace vespalib::tensor
} // namespace vespalib
