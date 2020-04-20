// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "double.h"
#include <vespa/searchlib/fef/featurenamebuilder.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/stash.h>
#include <cassert>

namespace search::fef::test {

void
DoubleExecutor::execute(uint32_t)
{
    assert(inputs().size() == _cnt);
    assert(outputs().size() == _cnt);
    for (uint32_t i = 0; i < _cnt; ++i) {
        outputs().set_number(i, inputs().get_number(i) * 2);
    }
}


DoubleBlueprint::DoubleBlueprint() :
    Blueprint("double"),
    _cnt(0)
{
}

void
DoubleBlueprint::visitDumpFeatures(const IIndexEnvironment & indexEnv, IDumpFeatureVisitor & visitor) const
{
    (void) indexEnv;
    (void) visitor;
}

bool
DoubleBlueprint::setup(const IIndexEnvironment & indexEnv, const StringVector & params)
{
    (void) indexEnv;
    for (uint32_t i = 0; i < params.size(); ++i) {
        defineInput(params[i]);
    }
    for (uint32_t i = 0; i < params.size(); ++i) {
        vespalib::asciistream name;
        name << i;
        vespalib::asciistream desc;
        desc << "doubled value " << i;
        describeOutput(name.str(), desc.str());
    }
    _cnt = params.size();
    return true;
}

FeatureExecutor &
DoubleBlueprint::createExecutor(const IQueryEnvironment &queryEnv, vespalib::Stash &stash) const
{
    (void) queryEnv;
    return stash.create<DoubleExecutor>(_cnt);
}

}
