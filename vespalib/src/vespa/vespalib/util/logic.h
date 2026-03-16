// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib {

// Material implication: P ==> Q
[[nodiscard]] constexpr bool implies(const bool p, const bool q) noexcept {
    return !p || q; // (P ==> Q) <==> (~P \/ Q)
}

} // namespace vespalib
