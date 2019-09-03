// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "isearchcontext.h"
#include <vespa/searchlib/query/tree/node.h>
#include <vespa/searchlib/queryeval/blueprint.h>

namespace search::queryeval {
    class SameElementBlueprint;
    class AndBlueprint;
}
namespace proton::matching {

class SameElementBuilder
{
private:
    const search::queryeval::IRequestContext                &_requestContext;
    ISearchContext                                          &_context;
    std::unique_ptr<search::queryeval::SameElementBlueprint> _sameElement;
    std::unique_ptr<search::queryeval::AndBlueprint>         _andFilter;
public:
    SameElementBuilder(const search::queryeval::IRequestContext &requestContext, ISearchContext &context);
    ~SameElementBuilder();
    void add_child(search::query::Node &node);
    search::queryeval::Blueprint::UP build();
};

}
