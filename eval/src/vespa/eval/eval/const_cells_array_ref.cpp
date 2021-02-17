#include "const_cells_array_ref.h"
#include "cells_array_ref.h"

namespace vespalib::eval {

template <typename T>
CellsArrayRef<T>
ConstCellsArrayRef<T>::unconstify() const {
    T * data = const_cast<T *>(_data);
    return CellsArrayRef<T>(data, _size);
}

template class ConstCellsArrayRef<double>;
template class ConstCellsArrayRef<float>;

CellsArrayRef<bool>
ConstCellsArrayRef<bool>::unconstify() const {
    uint64_t * data = const_cast<uint64_t *>(_data);
    return CellsArrayRef<bool>(data, _size, _offset);
}

};

