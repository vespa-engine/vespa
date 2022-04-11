// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <optional>
#include <cstdint>
#include <span>

namespace vespalib {

/**
 * LevenshteinDistance::calculate implements basic Levenshtein distance algorithm
 * with early stopping if the distance is already too high.
 * If the distance is above threshold method would return empty optional,
 * if the distance is below or equal to it, the distance will be wrapped in optional.
 * The types it's built upon are uint32 and it used to compare codepoints from terms,
 * but in future code can be generalized.
 *
 * Algorithm is based off Java implementation from commons-text library
 */
class LevenshteinDistance {
public:
    static std::optional<uint32_t> calculate(std::span<const uint32_t> left, std::span<const uint32_t> right, uint32_t threshold);
};

}
