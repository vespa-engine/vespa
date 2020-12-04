// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_cells_value.h"

namespace vespalib::eval {

template<typename T> DenseCellsValue<T>::~DenseCellsValue() = default;

template class DenseCellsValue<double>;
template class DenseCellsValue<float>;

}
