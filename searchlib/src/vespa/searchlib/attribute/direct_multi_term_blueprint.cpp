// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "direct_multi_term_blueprint.h"
#include "direct_multi_term_blueprint.hpp"
#include <vespa/searchlib/queryeval/dot_product_search.h>
#include <vespa/searchlib/queryeval/weighted_set_term_search.h>

namespace search::attribute {

template class DirectMultiTermBlueprint<IDocidWithWeightPostingStore, queryeval::WeightedSetTermSearch>;
template class DirectMultiTermBlueprint<IDocidWithWeightPostingStore, queryeval::DotProductSearch>;

}

