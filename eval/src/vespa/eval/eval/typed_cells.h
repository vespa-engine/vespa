// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/cell_type.h>
#include <span>

namespace vespalib::eval {

// Low-level typed cells reference

struct TypedCells {
    const void *data;
    size_t      size:55;
    bool        _non_existing_attribute_value:1;
    CellType    type;

    explicit TypedCells(std::span<const double> cells) noexcept : data(cells.data()), size(cells.size()), _non_existing_attribute_value(false), type(CellType::DOUBLE) {}
    explicit TypedCells(std::span<const float> cells) noexcept : data(cells.data()), size(cells.size()), _non_existing_attribute_value(false), type(CellType::FLOAT) {}
    explicit TypedCells(std::span<const BFloat16> cells) noexcept : data(cells.data()), size(cells.size()), _non_existing_attribute_value(false), type(CellType::BFLOAT16) {}
    explicit TypedCells(std::span<const Int8Float> cells) noexcept : data(cells.data()), size(cells.size()), _non_existing_attribute_value(false), type(CellType::INT8) {}

    TypedCells() noexcept : data(nullptr), size(0), _non_existing_attribute_value(false), type(CellType::DOUBLE) {}
    TypedCells(const void *dp, CellType ct, size_t sz) noexcept : data(dp), size(sz), _non_existing_attribute_value(false), type(ct) {}

    static TypedCells create_non_existing_attribute_value(const void *dp, CellType ct, size_t sz) {
        TypedCells cells(dp, ct, sz);
        cells._non_existing_attribute_value = true;
        return cells;
    }

    template <typename T> bool check_type() const noexcept  { return check_cell_type<T>(type); }

    template <typename T> std::span<const T> typify() const noexcept {
        assert(check_type<T>());
        return std::span<const T>((const T *)data, size);
    }
    template <typename T> std::span<const T> unsafe_typify() const noexcept {
        return std::span<const T>((const T *)data, size);
    }

    TypedCells(TypedCells &&other) noexcept = default;
    TypedCells(const TypedCells &other) noexcept = default;
    TypedCells & operator= (TypedCells &&other) noexcept = default;
    TypedCells & operator= (const TypedCells &other) noexcept = default;
    /**
     * This signals that this actually points to a value that is the default value
     * when no value has set for the attribute.
     * TODO: This does not belong here, but as it is used as an interface multiple places it must be so
     *       until we come up with a better solution.
     */
    bool non_existing_attribute_value() const noexcept { return _non_existing_attribute_value; }
};

} // namespace
