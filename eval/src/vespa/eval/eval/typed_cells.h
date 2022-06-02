// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/arrayref.h>
#include <vespa/eval/eval/cell_type.h>

namespace vespalib::eval {

// Low-level typed cells reference

struct TypedCells {
    const void *data;
    CellType type;
    size_t size:56;

    explicit TypedCells(ConstArrayRef<double> cells) : data(cells.begin()), type(CellType::DOUBLE), size(cells.size()) {}
    explicit TypedCells(ConstArrayRef<float> cells) : data(cells.begin()), type(CellType::FLOAT), size(cells.size()) {}
    explicit TypedCells(ConstArrayRef<BFloat16> cells) : data(cells.begin()), type(CellType::BFLOAT16), size(cells.size()) {}
    explicit TypedCells(ConstArrayRef<Int8Float> cells) : data(cells.begin()), type(CellType::INT8), size(cells.size()) {}

    TypedCells() noexcept : data(nullptr), type(CellType::DOUBLE), size(0) {}
    TypedCells(const void *dp, CellType ct, size_t sz) noexcept : data(dp), type(ct), size(sz) {}

    template <typename T> bool check_type() const { return vespalib::eval::check_cell_type<T>(type); }

    template <typename T> ConstArrayRef<T> typify() const {
        assert(check_type<T>());
        return ConstArrayRef<T>((const T *)data, size);
    }
    template <typename T> ConstArrayRef<T> unsafe_typify() const {
        return ConstArrayRef<T>((const T *)data, size);
    }

    TypedCells(TypedCells &&other) = default;
    TypedCells(const TypedCells &other) = default;
    TypedCells & operator= (TypedCells &&other) = default;
    TypedCells & operator= (const TypedCells &other) = default;
};

} // namespace
