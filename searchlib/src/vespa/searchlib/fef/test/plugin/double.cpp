// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".fef.double");
#include "double.h"

#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/searchlib/fef/featurenamebuilder.h>

namespace search {
namespace fef {
namespace test {

void
DoubleExecutor::execute(MatchData & data)
{
    assert(inputs().size() == _cnt);
    assert(outputs().size() == _cnt);
    for (uint32_t i = 0; i < _cnt; ++i) {
        *data.resolveFeature(outputs()[i]) = *data.resolveFeature(inputs()[i]) * 2;
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

} // namespace test
} // namespace fef
} // namespace search
