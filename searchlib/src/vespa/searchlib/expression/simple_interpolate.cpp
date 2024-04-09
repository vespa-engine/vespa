// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_interpolate.h"
#include <cstddef>

namespace search::expression {

double
simple_interpolate(const std::vector<double>& v, double lookup) noexcept
{
    if (v.empty() || lookup < v[0]) {
        return 0;
    }
    for (size_t i = 1; i < v.size(); ++i) {
        if (lookup < v[i]) {
            double total = v[i] - v[i - 1];
            double above = lookup - v[i - 1];
            double result = i - 1;
            result += (above / total);
            return result;
        }
    }
    return v.size() - 1;
}

}
