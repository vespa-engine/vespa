// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "isearchcontext.h"
#include <vespa/searchlib/query/tree/node.h>
#include <vespa/searchlib/queryeval/blueprint.h>

namespace proton::matching {

struct BlueprintBuilder {
    /**
     * Build a tree of blueprints from the query tree and inject
     * blueprint meta-data back into corresponding query tree nodes.
     */
    static search::queryeval::Blueprint::UP
    build(const search::queryeval::IRequestContext & requestContext,
          search::query::Node &node,
          ISearchContext &context);
};

}
