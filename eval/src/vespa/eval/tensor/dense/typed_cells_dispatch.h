// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/typed_cells.h>

namespace vespalib::tensor {

using CellType = vespalib::eval::ValueType::CellType;
using TypedCells = vespalib::eval::TypedCells;

template <typename TGT, typename... Args>
decltype(auto) dispatch_1(const TypedCells &a, Args &&...args) {
    switch (a.type) {
        case CellType::DOUBLE: return TGT::call(a.unsafe_typify<double>(), std::forward<Args>(args)...);
        case CellType::FLOAT:  return TGT::call(a.unsafe_typify<float>(),  std::forward<Args>(args)...);
    }
    abort();
}

template <typename TGT, typename A1, typename... Args>
decltype(auto) dispatch_2(A1 &&a, const TypedCells &b, Args &&...args) {
    switch (b.type) {
        case CellType::DOUBLE: return dispatch_1<TGT>(std::forward<A1>(a), b.unsafe_typify<double>(), std::forward<Args>(args)...);
        case CellType::FLOAT:  return dispatch_1<TGT>(std::forward<A1>(a), b.unsafe_typify<float>(),  std::forward<Args>(args)...);
    }
    abort();
}

struct GetCell {
    template<typename T>
    static double call(ConstArrayRef<T> arr, size_t idx) {
	return arr[idx];
    }
    static double from(TypedCells src, size_t idx) {
        return dispatch_1<GetCell>(src, idx);
    }
};

} // namespace
