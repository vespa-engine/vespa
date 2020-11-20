// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/typify.h>
#include <cstdint>

namespace vespalib::eval {

enum class CellType : char { FLOAT, DOUBLE };

// utility templates

template <typename CT> inline bool check_cell_type(CellType type);
template <> inline bool check_cell_type<double>(CellType type) { return (type == CellType::DOUBLE); }
template <> inline bool check_cell_type<float>(CellType type) { return (type == CellType::FLOAT); }

template <typename LCT, typename RCT> struct UnifyCellTypes{};
template <> struct UnifyCellTypes<double, double> { using type = double; };
template <> struct UnifyCellTypes<double, float>  { using type = double; };
template <> struct UnifyCellTypes<float,  double> { using type = double; };
template <> struct UnifyCellTypes<float,  float>  { using type = float; };

template <typename CT> inline CellType get_cell_type();
template <> inline CellType get_cell_type<double>() { return CellType::DOUBLE; }
template <> inline CellType get_cell_type<float>() { return CellType::FLOAT; }

struct CellTypeUtils {
    template <typename CT> static constexpr bool check(CellType type);
    template <typename CT> static constexpr CellType get_type();

    static constexpr uint32_t DOUBLE_ID = 0;
    static constexpr uint32_t FLOAT_ID = 1;

    static constexpr uint32_t id_of(CellType cell_type) {
        switch (cell_type) {
        case CellType::DOUBLE: return DOUBLE_ID;
        case CellType::FLOAT: return FLOAT_ID;
        }
        bad_argument((uint32_t)cell_type);
    }

    static constexpr CellType type_of(uint32_t id) {
        switch (id) {
        case DOUBLE_ID: return CellType::DOUBLE;
        case FLOAT_ID: return CellType::FLOAT;
        }
        bad_argument(id);
    }

    static void bad_argument [[ noreturn ]] (uint32_t id);

    static constexpr uint32_t alignment(CellType cell_type) {
        switch (cell_type) {
        case CellType::DOUBLE: return sizeof(double);
        case CellType::FLOAT: return sizeof(float);
        }
        bad_argument((uint32_t)cell_type);
    }

    static constexpr size_t mem_size(CellType cell_type, size_t sz) {
        switch (cell_type) {
        case CellType::DOUBLE: return sz * sizeof(double);
        case CellType::FLOAT:  return sz * sizeof(float);
        }
        bad_argument((uint32_t)cell_type);
    }
};

template <> constexpr bool 
CellTypeUtils::check<double>(CellType type) { return (type == CellType::DOUBLE); }

template <> constexpr bool 
CellTypeUtils::check<float>(CellType type) { return (type == CellType::FLOAT); }

template <> constexpr CellType
CellTypeUtils::get_type<double>() { return CellType::DOUBLE; }

template <> constexpr CellType
CellTypeUtils::get_type<float>() { return CellType::FLOAT; }

struct TypifyCellType {
    template <typename T> using Result = TypifyResultType<T>;
    template <typename F> static decltype(auto) resolve(CellType value, F &&f) {
        switch(value) {
        case CellType::DOUBLE: return f(Result<double>());
        case CellType::FLOAT:  return f(Result<float>());
        }
        CellTypeUtils::bad_argument((uint32_t)value);
    }
};

} // namespace
