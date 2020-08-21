// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <assert.h>
#include <vespa/vespalib/util/arrayref.h>
#include <vespa/eval/eval/value_type.h>

namespace vespalib::tensor {

// Low-level typed cells reference

using CellType = vespalib::eval::ValueType::CellType;

struct TypedCells {
    const void *data;
    CellType type;
    size_t size:56;

    explicit TypedCells(ConstArrayRef<double> cells) : data(cells.begin()), type(CellType::DOUBLE), size(cells.size()) {}
    explicit TypedCells(ConstArrayRef<float> cells) : data(cells.begin()), type(CellType::FLOAT), size(cells.size()) {}

    TypedCells() : data(nullptr), type(CellType::DOUBLE), size(0) {}
    TypedCells(const void *dp, CellType ct, size_t sz) : data(dp), type(ct), size(sz) {}

    template <typename T> bool check_type() const { return vespalib::eval::check_cell_type<T>(type); }
    template <typename T> ConstArrayRef<T> typify() const {
        assert(check_type<T>());
        return ConstArrayRef<T>((const T *)data, size);
    }
    template <typename T> ConstArrayRef<T> unsafe_typify() const {
        return ConstArrayRef<T>((const T *)data, size);
    }

    double get(size_t idx) const {
        if (type == CellType::DOUBLE) {
            const double *p = (const double *)data;
            return p[idx];
        }
        if (type == CellType::FLOAT) {
            const float *p = (const float *)data;
            return p[idx];
        }
        abort();
    }

    TypedCells(TypedCells &&other) = default;
    TypedCells(const TypedCells &other) = default;
    TypedCells & operator= (TypedCells &&other) = default;
    TypedCells & operator= (const TypedCells &other) = default;
};

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

} // namespace
