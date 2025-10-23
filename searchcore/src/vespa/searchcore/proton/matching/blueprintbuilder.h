// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "isearchcontext.h"
#include <vespa/searchlib/query/tree/node.h>
#include <vespa/searchlib/queryeval/blueprint.h>

namespace search::fef { class MatchDataLayout; }
namespace search::queryeval { class IRequestContext; }

namespace proton::matching {

struct BlueprintBuilder {
    using IRequestContext = search::queryeval::IRequestContext;
    using Blueprint = search::queryeval::Blueprint;
    using Node = search::query::Node;
    using MatchDataLayout = search::fef::MatchDataLayout;
    /**
     * Build a tree of blueprints from the query tree and inject
     * blueprint meta-data back into corresponding query tree nodes.
     */
    static Blueprint::UP
    build(const IRequestContext & requestContext, Node &node, ISearchContext &context, MatchDataLayout &global_layout) {
        return build(requestContext, node, {}, context, global_layout);
    }
    static Blueprint::UP
    build(const IRequestContext & requestContext, Node &node, Blueprint::UP whiteList, ISearchContext &context, MatchDataLayout &global_layout);
};

}
