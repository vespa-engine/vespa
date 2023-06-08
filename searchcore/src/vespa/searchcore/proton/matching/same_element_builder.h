// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "isearchcontext.h"
#include <vespa/searchlib/query/tree/node.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/same_element_blueprint.h>

namespace search::queryeval {
    class IRequestContext;
    class FieldSpec;
}

namespace proton::matching {

class SameElementBuilder
{
public:
    SameElementBuilder(const search::queryeval::IRequestContext &requestContext, ISearchContext &context,
                       const search::queryeval::FieldSpec &field, bool expensive);
    void add_child(search::query::Node &node);
    search::queryeval::Blueprint::UP build();
private:
    const search::queryeval::IRequestContext                &_requestContext;
    ISearchContext                                          &_context;
    std::unique_ptr<search::queryeval::SameElementBlueprint> _result;
};

}
