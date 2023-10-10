// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "query.h"
#include <vespa/searchlib/features/valuefeature.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/locale/c.h>
#include <vespa/vespalib/util/stash.h>
#include <sstream>

namespace search::fef::test {

QueryBlueprint::QueryBlueprint() :
    Blueprint("test_query"),
    _key()
{
    // empty
}

bool
QueryBlueprint::setup(const IIndexEnvironment &indexEnv, const StringVector &params)
{
    (void) indexEnv;
    if (params.size() != 1) {
        return false;
    }
    _key = params[0];
    describeOutput("value", "the parameter looked up in the rank properties and converted to a float");
    return true;
}

FeatureExecutor &
QueryBlueprint::createExecutor(const IQueryEnvironment &queryEnv, vespalib::Stash &stash) const
{
    std::vector<feature_t> values;
    std::string val = queryEnv.getProperties().lookup(_key).get("0.0");
    values.push_back(vespalib::locale::c::strtod(val.data(), nullptr));
    return stash.create<search::features::ValueExecutor>(values);
}

}
