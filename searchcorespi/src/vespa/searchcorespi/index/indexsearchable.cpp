// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "indexsearchable.h"
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>

using namespace search::queryeval;

namespace searchcorespi {

IndexSearchable::Blueprint::UP
IndexSearchable::createBlueprint(const IRequestContext & requestContext,
                                 const FieldSpecList &fields,
                                 const Node &term,
                                 const IAttributeContext &attrCtx)
{
    if (fields.empty()) {
        return Blueprint::UP(new EmptyBlueprint());
    }
    if (fields.size() == 1) {
        return createBlueprint(requestContext, fields[0], term, attrCtx);
    }
    OrBlueprint *b = new OrBlueprint();
    Blueprint::UP result(b);
    for (size_t i = 0; i < fields.size(); ++i) {
        b->addChild(createBlueprint(requestContext, fields[i], term, attrCtx));
    }
    return result;
}

} // namespace searchcorespi

