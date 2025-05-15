// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cmath>
#include <string>
#include <optional>
#include "int8float.h"
#include <vespa/vespalib/util/bfloat16.h>

namespace vespalib::eval {

enum class CellOrder : char { MAX, MIN };

struct CellOrderMAX {
    static constexpr bool cmp(Int8Float a, Int8Float b) noexcept {
        return a.get_bits() > b.get_bits();
    }
    static constexpr bool cmp(auto a, auto b) noexcept {
        return std::isnan(b) ? !std::isnan(a) : a > b;
    }
    static constexpr bool cmp(vespalib::BFloat16 a, vespalib::BFloat16 b) noexcept {
        return cmp(a.to_float(), b.to_float());
    }
    constexpr bool operator()(auto a, auto b) noexcept { return cmp(a, b); }
};

struct CellOrderMIN {
    static constexpr bool cmp(Int8Float a, Int8Float b) noexcept {
        return a.get_bits() < b.get_bits();
    }
    static constexpr bool cmp(auto a, auto b) noexcept {
        return std::isnan(b) ? !std::isnan(a) : a < b;
    }
    static constexpr bool cmp(vespalib::BFloat16 a, vespalib::BFloat16 b) noexcept {
        return cmp(a.to_float(), b.to_float());
    }
    constexpr bool operator()(auto a, auto b) noexcept { return cmp(a, b); }
};

std::string as_string(CellOrder cell_order);

std::optional<CellOrder> cell_order_from_string(const std::string &str);

} // namespace
