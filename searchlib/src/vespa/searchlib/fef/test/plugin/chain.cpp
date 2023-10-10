// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "chain.h"
#include <vespa/vespalib/util/stash.h>
#include <sstream>

namespace search::fef::test {

ChainExecutor::ChainExecutor() :
    FeatureExecutor()
{
}

void
ChainExecutor::execute(uint32_t)
{
    outputs().set_number(0, inputs().get_number(0));
}


ChainBlueprint::ChainBlueprint() :
    Blueprint("chain")
{
}

bool
ChainBlueprint::setup(const IIndexEnvironment & indexEnv, const StringVector & params)
{
    (void) indexEnv;
    if (params.size() != 3) { // [type, children, value]
        return false;
    }
    const std::string & type = params[0];
    const std::string & children = params[1];
    const std::string & value = params[2];

    uint32_t numChildren;
    std::istringstream iss(children);
    iss >> std::dec >> numChildren;
    std::ostringstream oss;
    if (numChildren == 0) {
        return false;
    }
    if (numChildren == 1) {
        if (type == "basic") {
            oss << "value(" << value << ")"; // value = input to value executor
            defineInput(oss.str());
        } else if (type == "cycle") {
            oss << "chain(" << type << "," << value << "," << value << ")"; // value = where to insert the cycle
            defineInput(oss.str());
        } else {
            return false;
        }
    } else {
        oss << "chain(" << type << "," << (numChildren - 1) << "," << value << ")";
        defineInput(oss.str());
    }
    describeOutput("out", "chain");
    return true;
}

FeatureExecutor &
ChainBlueprint::createExecutor(const IQueryEnvironment &queryEnv, vespalib::Stash &stash) const
{
    (void) queryEnv;
    return stash.create<ChainExecutor>();
}

}
