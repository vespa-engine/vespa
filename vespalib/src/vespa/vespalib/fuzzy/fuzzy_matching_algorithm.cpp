// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fuzzy_matching_algorithm.h"

namespace vespalib {

namespace {

const vespalib::string brute_force = "brute_force";
const vespalib::string dfa_implicit = "dfa_implicit";
const vespalib::string dfa_explicit = "dfa_explicit";
const vespalib::string dfa_table = "dfa_table";

}

vespalib::string
to_string(FuzzyMatchingAlgorithm algo)
{
    switch (algo) {
        case FuzzyMatchingAlgorithm::BruteForce:
            return brute_force;
        case FuzzyMatchingAlgorithm::DfaImplicit:
            return dfa_implicit;
        case FuzzyMatchingAlgorithm::DfaExplicit:
            return dfa_explicit;
        case FuzzyMatchingAlgorithm::DfaTable:
            return dfa_table;
        default:
            return "";
    }
}

FuzzyMatchingAlgorithm
fuzzy_matching_algorithm_from_string(const vespalib::string& algo,
                                     FuzzyMatchingAlgorithm default_algo)
{
    if (algo == brute_force) {
        return FuzzyMatchingAlgorithm::BruteForce;
    } else if (algo == dfa_implicit) {
        return FuzzyMatchingAlgorithm::DfaImplicit;
    } else if (algo == dfa_explicit) {
        return FuzzyMatchingAlgorithm::DfaExplicit;
    } else if (algo == dfa_table) {
        return FuzzyMatchingAlgorithm::DfaTable;
    }
    return default_algo;
}

std::ostream&
operator<<(std::ostream& out, FuzzyMatchingAlgorithm algo)
{
    out << to_string(algo);
    return out;
}

}
