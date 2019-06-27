// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <assert.h>
#include <vespa/vespalib/util/arrayref.h>
#include <vespa/eval/eval/value_type.h>

namespace vespalib::tensor {

// Low-level typed cells reference

using CellType = vespalib::eval::ValueType::CellType;

template <typename T> inline bool check_type(CellType type);
template <> inline bool check_type<double>(CellType type) { return (type == CellType::DOUBLE); }
template <> inline bool check_type<float>(CellType type) { return (type == CellType::FLOAT); }

template <typename CT> struct ToCellType {};
template <> struct ToCellType<double> {
    static constexpr CellType cell_type() { return CellType::DOUBLE; };
};
template <> struct ToCellType<float> {
    static constexpr CellType cell_type() { return CellType::FLOAT; };
};

template<typename LCT, typename RCT> struct OutputCellType;
template<> struct OutputCellType<double, double> {
    typedef double output_type;
    static constexpr CellType output_cell_type() { return CellType::DOUBLE; };
    static const eval::ValueType & result_type(const eval::ValueType &leftType,
                                               const eval::ValueType &) { return leftType; }
};
template<> struct OutputCellType<float, double> {
    typedef double output_type;
    static constexpr CellType output_cell_type() { return CellType::DOUBLE; };
    static const eval::ValueType & result_type(const eval::ValueType &,
                                               const eval::ValueType &rightType) { return rightType; }
};
template<> struct OutputCellType<double, float> {
    typedef double output_type;
    static constexpr CellType output_cell_type() { return CellType::DOUBLE; };
    static const eval::ValueType & result_type(const eval::ValueType &leftType,
                                               const eval::ValueType &) { return leftType; }
};
template<> struct OutputCellType<float, float> {
    typedef float output_type;
    static constexpr CellType output_cell_type() { return CellType::FLOAT; };
    static const eval::ValueType & result_type(const eval::ValueType &leftType,
                                               const eval::ValueType &) { return leftType; }
};

struct TypedCells {
    const void *data;
    const CellType type;
    size_t size:56;

    explicit TypedCells(ConstArrayRef<double> cells) : data(cells.begin()), type(CellType::DOUBLE), size(cells.size()) {}
    explicit TypedCells(ConstArrayRef<float> cells) : data(cells.begin()), type(CellType::FLOAT), size(cells.size()) {}

    TypedCells(const void *dp, CellType ct, size_t sz) : data(dp), type(ct), size(sz) {}
    TypedCells(CellType ct) : data(nullptr), type(ct), size(0) {}

    template <typename T> bool check_type() const { return vespalib::tensor::check_type<T>(type); }
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

    TypedCells & operator= (const TypedCells &other) {
        assert(type == other.type);
        data = other.data;
        size = other.size;
        return *this;
    }
};

template <typename TGT, typename... Args>
auto dispatch_1(const TypedCells &a, Args &&...args) {
    switch(a.type) {
        case CellType::DOUBLE: return TGT::call(a.unsafe_typify<double>(), std::forward<Args>(args)...);
        case CellType::FLOAT:  return TGT::call(a.unsafe_typify<float>(),  std::forward<Args>(args)...);
    }
    abort();
}

template <typename TGT, typename A1, typename... Args>
auto dispatch_2(A1 &&a, const TypedCells &b, Args &&...args) {
    switch(b.type) {
        case CellType::DOUBLE: return dispatch_1<TGT>(std::forward<A1>(a), b.unsafe_typify<double>(), std::forward<Args>(args)...);
        case CellType::FLOAT:  return dispatch_1<TGT>(std::forward<A1>(a), b.unsafe_typify<float>(),  std::forward<Args>(args)...);
    }
    abort();
}

} // namespace
