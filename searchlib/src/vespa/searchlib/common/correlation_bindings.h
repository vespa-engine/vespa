// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>
#include <string>
#include <string_view>
#include <variant>
#include <utility>
#include <vector>

namespace search {
using CorrelationValue = std::variant<int64_t, std::string_view>;
using CorrelationBinding = std::pair<std::string, CorrelationValue>;
using CorrelationBindings = std::vector<CorrelationBinding>;
}
