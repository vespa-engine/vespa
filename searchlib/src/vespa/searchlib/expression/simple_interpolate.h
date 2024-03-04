// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>

namespace search::expression {

/*
 * Perform simple interpolation for interpolatedlookup function
 * in grouping expression.
 */
double simple_interpolate(const std::vector<double>& v, double lookup) noexcept;

}
