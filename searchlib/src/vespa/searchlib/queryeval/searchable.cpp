// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchable.h"
#include "leaf_blueprints.h"
#include "intermediate_blueprints.h"

namespace search::queryeval {

Blueprint::UP
Searchable::createBlueprint(const IRequestContext & requestContext,
                            const FieldSpecList &fields,
                            const search::query::Node &term)
{
    if (fields.empty()) {
        return Blueprint::UP(new EmptyBlueprint());
    }
    if (fields.size() == 1) {
        return createBlueprint(requestContext, fields[0], term);
    }
    OrBlueprint *b = new OrBlueprint();
    Blueprint::UP result(b);
    for (size_t i = 0; i < fields.size(); ++i) {
        b->addChild(createBlueprint(requestContext, fields[i], term));
    }
    return result;
}

}
