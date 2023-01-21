// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/arrayref.h>
#include <vespa/eval/eval/cell_type.h>

namespace vespalib::eval {

// Low-level typed cells reference

struct TypedCells {
    const void *data;
    size_t size:56;
    CellType type;

    explicit TypedCells(ConstArrayRef<double> cells) : data(cells.begin()), size(cells.size()), type(CellType::DOUBLE) {}
    explicit TypedCells(ConstArrayRef<float> cells) : data(cells.begin()), size(cells.size()), type(CellType::FLOAT) {}
    explicit TypedCells(ConstArrayRef<BFloat16> cells) : data(cells.begin()), size(cells.size()), type(CellType::BFLOAT16) {}
    explicit TypedCells(ConstArrayRef<Int8Float> cells) : data(cells.begin()), size(cells.size()), type(CellType::INT8) {}

    TypedCells() noexcept : data(nullptr), size(0), type(CellType::DOUBLE) {}
    TypedCells(const void *dp, CellType ct, size_t sz) noexcept : data(dp), size(sz), type(ct) {}

    template <typename T> bool check_type() const { return vespalib::eval::check_cell_type<T>(type); }

    template <typename T> ConstArrayRef<T> typify() const {
        assert(check_type<T>());
        return ConstArrayRef<T>((const T *)data, size);
    }
    template <typename T> ConstArrayRef<T> unsafe_typify() const {
        return ConstArrayRef<T>((const T *)data, size);
    }

    TypedCells(TypedCells &&other) noexcept = default;
    TypedCells(const TypedCells &other) noexcept = default;
    TypedCells & operator= (TypedCells &&other) noexcept = default;
    TypedCells & operator= (const TypedCells &other) noexcept = default;
};

} // namespace
