// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <optional>
#include <cstdint>
#include <vector>

namespace vespalib {

class LevensteinDistance {
public:
    static std::optional<uint32_t> calculate(const std::vector<uint32_t>& left, const std::vector<uint32_t>& right, uint32_t threshold);
};

}
