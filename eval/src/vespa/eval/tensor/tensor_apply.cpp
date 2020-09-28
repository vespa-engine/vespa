// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_apply.h"
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace vespalib::tensor {

template <class TensorT>
TensorApply<TensorT>::TensorApply(const TensorImplType &tensor,
                                  const CellFunction &func)
    : Parent(tensor.fast_type())
{
    for (const auto &cell : tensor.my_cells()) {
        _builder.insertCell(cell.first, func.apply(cell.second));
    }
}

template class TensorApply<SparseTensor>;

}
