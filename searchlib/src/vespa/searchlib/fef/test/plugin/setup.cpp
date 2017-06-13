// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "cfgvalue.h"
#include "chain.h"
#include "double.h"
#include "query.h"
#include "setup.h"
#include "staticrank.h"
#include "sum.h"
#include <vespa/searchlib/fef/blueprint.h>

namespace search {
namespace fef {
namespace test {

void setup_fef_test_plugin(IBlueprintRegistry & registry)
{
    // register blueprints
    registry.addPrototype(Blueprint::SP(new DoubleBlueprint()));
    registry.addPrototype(Blueprint::SP(new SumBlueprint()));
    registry.addPrototype(Blueprint::SP(new StaticRankBlueprint()));
    registry.addPrototype(Blueprint::SP(new ChainBlueprint()));
    registry.addPrototype(Blueprint::SP(new CfgValueBlueprint()));
    registry.addPrototype(Blueprint::SP(new QueryBlueprint()));
}

} // namespace test
} // namespace fef
} // namespace search
