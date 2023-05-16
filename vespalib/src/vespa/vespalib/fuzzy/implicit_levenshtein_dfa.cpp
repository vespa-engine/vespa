// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "implicit_levenshtein_dfa.hpp"

namespace vespalib::fuzzy {

template class ImplicitLevenshteinDfa<FixedMaxEditDistanceTraits<1>>;
template class ImplicitLevenshteinDfa<FixedMaxEditDistanceTraits<2>>;

}
