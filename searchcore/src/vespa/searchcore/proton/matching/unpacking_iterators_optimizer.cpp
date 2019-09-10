// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "unpacking_iterators_optimizer.h"

namespace proton::matching {

search::query::Node::UP
UnpackingIteratorsOptimizer::optimize(search::query::Node::UP root,
                                      bool has_white_list,
                                      bool split_unpacking_iterators,
                                      bool delay_unpacking_iterators)
{
    (void) has_white_list;
    (void) split_unpacking_iterators;
    (void) delay_unpacking_iterators;
    return std::move(root); // nop for now
}

}
