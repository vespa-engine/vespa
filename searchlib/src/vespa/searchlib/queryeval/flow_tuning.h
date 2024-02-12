// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once
#include <cmath>
#include <cstddef>

namespace search::queryeval::flow {

inline double heap_cost(double my_est, size_t num_children) {
    return my_est * std::log2(std::max(size_t(1),num_children));
}

}
