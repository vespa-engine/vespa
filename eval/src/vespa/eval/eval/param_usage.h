// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>

namespace vespalib {
namespace eval {

class Function;

/**
 * Calculate the expected number of times each parameter will be
 * used. Note: Correlation between condition checks and effects of
 * short-circuit evaluation and constant value optimizations are not
 * taken into account.
 *
 * @return expected parameter usage per parameter
 * @param function the function to analyze
 **/
std::vector<double> count_param_usage(const Function &function);

/**
 * Calculate the probability that each parameter will be used. Note:
 * Correlation between condition checks and effects of short-circuit
 * evaluation and constant value optimizations are not taken into
 * account.
 *
 * @return parameter usage probability per parameter
 * @param function the function to analyze
 **/
std::vector<double> check_param_usage(const Function &function);

} // namespace vespalib::eval
} // namespace vespalib
