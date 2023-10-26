// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cmath>

namespace search {

using HitRank = double;
constexpr HitRank default_rank_value = -HUGE_VAL;
constexpr HitRank zero_rank_value = 0.0;

} // namespace search

