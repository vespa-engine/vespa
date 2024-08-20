// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <ostream>
#include <string>

namespace vespalib {

/**
 * Algorithms that are supported for fuzzy matching.
 */
enum class FuzzyMatchingAlgorithm {
    BruteForce,
    DfaImplicit,
    DfaExplicit,
    DfaTable
};

std::string to_string(FuzzyMatchingAlgorithm algo);

FuzzyMatchingAlgorithm fuzzy_matching_algorithm_from_string(const std::string& algo,
                                                            FuzzyMatchingAlgorithm default_algo);

std::ostream& operator<<(std::ostream& out, FuzzyMatchingAlgorithm algo);

}
