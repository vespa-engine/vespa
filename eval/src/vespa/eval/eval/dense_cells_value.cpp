// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_cells_value.h"

namespace vespalib::eval {

template<typename T> DenseCellsValue<T>::~DenseCellsValue() = default;

template<typename T> MemoryUsage
DenseCellsValue<T>::get_memory_usage() const {
    auto usage = self_memory_usage<DenseCellsValue<T>>();
    usage.merge(vector_extra_memory_usage(_cells));
    return usage;
}

template class DenseCellsValue<double>;
template class DenseCellsValue<float>;

}
