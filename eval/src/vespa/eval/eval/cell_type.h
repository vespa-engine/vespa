// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/typify.h>
#include <vector>
#include <cstdint>
#include <cassert>

namespace vespalib::eval {

enum class CellType : char { FLOAT, DOUBLE };

// converts actual cell type to CellType enum value
template <typename CT> constexpr CellType get_cell_type();
template <> constexpr CellType get_cell_type<double>() { return CellType::DOUBLE; }
template <> constexpr CellType get_cell_type<float>() { return CellType::FLOAT; }

// check if the given CellType enum value and actual cell type match
template <typename CT> constexpr bool check_cell_type(CellType type) {
    return (type == get_cell_type<CT>());
}

// converts CellType enum value to actual cell type by using the
// return value as a type tag. usage:
// decltype(get_cell_value<my_cell_type>())
template <CellType cell_type> constexpr auto get_cell_value() {
    if constexpr (cell_type == CellType::DOUBLE) {
        return double();
    } else if constexpr (cell_type == CellType::FLOAT) {
        return float();
    } else {
        static_assert((cell_type == CellType::DOUBLE), "unknown cell type");
    }
}
template <CellType cell_type> using CellValueType = decltype(get_cell_value<cell_type>());

// simple CellMeta value wrapper to reduce template expansion
// -> for values that are results of operations that are not scalars
struct LimitedCellMetaNotScalar {
    const CellType cell_type;
};

// simple CellMeta value wrapper to reduce template expansion
// -> for values that are results of operations
struct LimitedCellMeta {
    const CellType cell_type;
    const bool is_scalar;
    constexpr LimitedCellMetaNotScalar not_scalar() const {
        assert(!is_scalar);
        return {cell_type};
    }
};

// simple CellMeta value wrapper to reduce template expansion
// -> for values that we known are not scalar
struct CellMetaNotScalar {
    const CellType cell_type;
};

// meta-information about the cell type and 'scalar-ness' of a value
struct CellMeta {
    const CellType cell_type;
    const bool is_scalar;
    constexpr CellMeta(CellType cell_type_in, bool is_scalar_in)
        : cell_type(cell_type_in), is_scalar(is_scalar_in)
    {
        // is_scalar -> double cell type
        assert(!is_scalar || (cell_type == CellType::DOUBLE));
    }
    constexpr bool is_limited() const {
        return ((cell_type == CellType::DOUBLE) || (cell_type == CellType::FLOAT));
    }
    constexpr LimitedCellMeta limit() const {
        assert(is_limited());
        return {cell_type, is_scalar};
    }
    constexpr CellMetaNotScalar not_scalar() const {
        assert(!is_scalar);
        return {cell_type};
    }

    constexpr CellMeta self() const { return *this; }

    constexpr bool eq(const CellMeta &rhs) const {
        return ((cell_type == rhs.cell_type) && (is_scalar == rhs.is_scalar));
    }

    // promote cell type to at least float
    constexpr CellMeta decay() const {
        if (cell_type == CellType::DOUBLE) {
            return self();
        }
        return {CellType::FLOAT, is_scalar};
    }

    // normalize to make sure scalar values have cell type double
    static constexpr CellMeta normalize(CellType cell_type, bool is_scalar) {
        if (is_scalar) {
            return CellMeta(CellType::DOUBLE, true);
        } else {
            return CellMeta(cell_type, false);
        }
    }

    // unify the cell meta across two values
    static constexpr CellMeta unify(CellMeta a, CellMeta b) {
        if (a.is_scalar) {
            return b;
        } else if (b.is_scalar) {
            return a;
        }
        if (a.cell_type == b.cell_type) {
            return {a.cell_type, false};
        } else if ((a.cell_type == CellType::DOUBLE) || (b.cell_type == CellType::DOUBLE)) {
            return {CellType::DOUBLE, false};
        } else {
            return {CellType::FLOAT, false};
        }
    }

    // convenience functions to be used for specific operations
    constexpr CellMeta map() const { return decay(); }
    constexpr CellMeta reduce(bool output_is_scalar) const {
        return normalize(cell_type, output_is_scalar).decay();
    }
    static constexpr CellMeta join(CellMeta a, CellMeta b) { return unify(a, b).decay(); }
    static constexpr CellMeta merge(CellMeta a, CellMeta b) { return unify(a, b).decay(); }
    static constexpr CellMeta concat(CellMeta a, CellMeta b) { return unify(a, b); }
    constexpr CellMeta peek(bool output_is_scalar) const {
        return normalize(cell_type, output_is_scalar);
    }
    constexpr CellMeta rename() const { return self(); }
};

struct TypifyCellType {
    template <typename T> using Result = TypifyResultType<T>;
    template <typename F> static decltype(auto) resolve(CellType value, F &&f) {
        switch (value) {
        case CellType::DOUBLE: return f(Result<double>());
        case CellType::FLOAT:  return f(Result<float>());
        }
        abort();
    }
};

struct TypifyCellMeta {
    template <CellMeta VALUE> using Result = TypifyResultValue<CellMeta, VALUE>;
    template <typename F> static decltype(auto) resolve(CellMeta value, F &&f) {
        if (value.is_scalar) {
            if (value.cell_type == CellType::DOUBLE) {
                return f(Result<CellMeta(CellType::DOUBLE, true)>());
            }
            abort();
        } else {
            return resolve(value.not_scalar(), std::forward<F>(f));
        }
    }
    template <typename F> static decltype(auto) resolve(CellMetaNotScalar value, F &&f) {
        switch (value.cell_type) {
        case CellType::DOUBLE: return f(Result<CellMeta(CellType::DOUBLE, false)>());
        case CellType::FLOAT:  return f(Result<CellMeta(CellType::FLOAT, false)>());
        }
        abort();
    }
    template <typename F> static decltype(auto) resolve(LimitedCellMeta value, F &&f) {
        if (value.is_scalar) {
            if (value.cell_type == CellType::DOUBLE) {
                return f(Result<CellMeta(CellType::DOUBLE, true)>());
            }
            abort();
        } else {
            return resolve(value.not_scalar(), std::forward<F>(f));
        }
    }
    template <typename F> static decltype(auto) resolve(LimitedCellMetaNotScalar value, F &&f) {
        switch (value.cell_type) {
        case CellType::DOUBLE: return f(Result<CellMeta(CellType::DOUBLE, false)>());
        case CellType::FLOAT:  return f(Result<CellMeta(CellType::FLOAT, false)>());
        default: break;
        }
        abort();
    }
};

struct CellTypeUtils {
    static uint32_t alignment(CellType cell_type);
    static size_t mem_size(CellType cell_type, size_t sz);
    static std::vector<CellType> list_types();
};

} // namespace
