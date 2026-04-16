// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <compare>
#include <cstdint>
#include <iosfwd>

namespace vespalib {

/*
 * Generation managed by GenerationHandler, used for structures where many readers can access the data while a single
 * writer is updating it. Readers must have a GenerationGuard.
 */
class Generation {
    using value_type = uint64_t;
    value_type _value;
public:
    constexpr Generation() noexcept : _value(0) { }
    constexpr explicit Generation(value_type value_) noexcept : _value(value_) { }
    [[nodiscard]] constexpr value_type value() const noexcept { return _value; }
    [[nodiscard]] constexpr auto operator<=>(const Generation& rhs) const noexcept = default;
    constexpr Generation& operator++() noexcept { ++_value; return *this; }
    [[nodiscard]] constexpr Generation operator+(value_type delta) const noexcept { return Generation(value() + delta); }
    [[nodiscard]] constexpr Generation operator-(value_type delta) const noexcept { return Generation(value() - delta); }
    [[nodiscard]] static constexpr Generation make_invalid() noexcept { return Generation() - 1; }
};

std::ostream& operator<<(std::ostream& os, const Generation& generation);

}
