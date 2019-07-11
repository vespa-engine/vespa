// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <assert.h>
#include <vespa/vespalib/util/arrayref.h>
#include <vespa/eval/eval/value_type.h>

namespace vespalib::tensor {

// Low-level typed cells reference

using CellType = vespalib::eval::ValueType::CellType;


template<typename LCT, typename RCT> struct OutputCellType;
template<> struct OutputCellType<double, double> {
    typedef double output_type;
    static constexpr CellType output_cell_type() { return CellType::DOUBLE; };
};
template<> struct OutputCellType<float, double> {
    typedef double output_type;
    static constexpr CellType output_cell_type() { return CellType::DOUBLE; };
};
template<> struct OutputCellType<double, float> {
    typedef double output_type;
    static constexpr CellType output_cell_type() { return CellType::DOUBLE; };
};
template<> struct OutputCellType<float, float> {
    typedef float output_type;
    static constexpr CellType output_cell_type() { return CellType::FLOAT; };
};

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

    TypedCells & operator= (const TypedCells &other) = default;
};

template <typename TGT, typename... Args>
auto dispatch_0(CellType ct, Args &&...args) {
    switch (ct) {
        case CellType::DOUBLE: return TGT::template call<double>(std::forward<Args>(args)...);
        case CellType::FLOAT:  return TGT::template call<float>(std::forward<Args>(args)...);
    }
    abort();
}

template <typename TGT, typename... Args>
auto dispatch_1(const TypedCells &a, Args &&...args) {
    switch (a.type) {
        case CellType::DOUBLE: return TGT::call(a.unsafe_typify<double>(), std::forward<Args>(args)...);
        case CellType::FLOAT:  return TGT::call(a.unsafe_typify<float>(),  std::forward<Args>(args)...);
    }
    abort();
}

template <typename TGT, typename A1, typename... Args>
auto dispatch_2(A1 &&a, const TypedCells &b, Args &&...args) {
    switch (b.type) {
        case CellType::DOUBLE: return dispatch_1<TGT>(std::forward<A1>(a), b.unsafe_typify<double>(), std::forward<Args>(args)...);
        case CellType::FLOAT:  return dispatch_1<TGT>(std::forward<A1>(a), b.unsafe_typify<float>(),  std::forward<Args>(args)...);
    }
    abort();
}

template <typename T, typename... Args>
auto select_1(CellType a_type) {
    switch(a_type) {
    case CellType::DOUBLE: return T::template get_fun<double, Args...>();
    case CellType::FLOAT:  return T::template get_fun<float, Args...>();
    }
    abort();
}

template <typename T>
auto select_2(CellType a_type, CellType b_type) {
    switch(b_type) {
    case CellType::DOUBLE: return select_1<T, double>(a_type);
    case CellType::FLOAT:  return select_1<T, float>(a_type);
    }
    abort();
}

} // namespace
