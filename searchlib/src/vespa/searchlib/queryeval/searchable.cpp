// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchable.h"
#include "leaf_blueprints.h"
#include "intermediate_blueprints.h"

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.queryeval.searchable");

namespace search::queryeval {

Blueprint::UP
Searchable::createBlueprint(const IRequestContext & requestContext,
                            const FieldSpecList &fields,
                            const search::query::Node &term)
{
    if (fields.empty()) {
        LOG(debug, "EmptyBlueprint: FieldSpecList is empty");
        return std::make_unique<EmptyBlueprint>();
    }
    if (fields.size() == 1) {
        return createBlueprint(requestContext, fields[0], term);
    }
    auto b = std::make_unique<OrBlueprint>();
    for (size_t i = 0; i < fields.size(); ++i) {
        b->addChild(createBlueprint(requestContext, fields[i], term));
    }
    return b;
}

}
