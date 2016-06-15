// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// $Id$

#include <vespa/fastos/fastos.h>
#include "create_blueprint_visitor_helper.h"
#include <vespa/searchlib/queryeval/leaf_blueprints.h>

namespace search {
namespace queryeval {

Blueprint::UP
CreateBlueprintVisitorHelper::getResult()
{
    return _result
        ? std::move(_result)
        : Blueprint::UP(new EmptyBlueprint(_field));
}

} // namespace search::queryeval
} // namespace search
