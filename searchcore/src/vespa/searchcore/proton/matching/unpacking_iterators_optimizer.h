// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/query/tree/node.h>

namespace proton::matching {

/**
 * Unpacking iterators are indirectly optimized by augmenting the
 * query tree and by tagging appropriate query tree nodes as
 * expensive.
 **/
struct UnpackingIteratorsOptimizer {
    static search::query::Node::UP optimize(search::query::Node::UP root,
                                            bool has_white_list,
                                            bool split_unpacking_iterators);
};

}
