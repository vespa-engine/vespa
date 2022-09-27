// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "setup.h"
#include "cfgvalue.h"
#include "chain.h"
#include "double.h"
#include "query.h"
#include "staticrank.h"
#include "sum.h"
#include "unbox.h"

namespace search::fef::test {

void setup_fef_test_plugin(IBlueprintRegistry & registry)
{
    // register blueprints
    registry.addPrototype(std::make_shared<DoubleBlueprint>());
    registry.addPrototype(std::make_shared<SumBlueprint>());
    registry.addPrototype(std::make_shared<StaticRankBlueprint>());
    registry.addPrototype(std::make_shared<ChainBlueprint>());
    registry.addPrototype(std::make_shared<CfgValueBlueprint>());
    registry.addPrototype(std::make_shared<QueryBlueprint>());
    registry.addPrototype(std::make_shared<UnboxBlueprint>());
}

} // namespace search::fef::test
