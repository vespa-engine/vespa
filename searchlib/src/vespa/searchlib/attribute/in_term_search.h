// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::attribute {

/**
 * Class used as template argument in DirectMultiTermBlueprint to configure it for the InTerm query operator.
 */
struct InTermSearch {
    static constexpr bool filter_search = true;
    static constexpr bool require_btree_iterators = false;
};

}
