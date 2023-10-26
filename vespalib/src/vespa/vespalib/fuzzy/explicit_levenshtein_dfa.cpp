// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "explicit_levenshtein_dfa.hpp"

namespace vespalib::fuzzy {

template class ExplicitLevenshteinDfaImpl<1>;
template class ExplicitLevenshteinDfaImpl<2>;
template class ExplicitLevenshteinDfaBuilder<FixedMaxEditDistanceTraits<1>>;
template class ExplicitLevenshteinDfaBuilder<FixedMaxEditDistanceTraits<2>>;

}
