// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".features.debug_wait");
#include "debug_wait.h"

namespace search {
namespace features {

//-----------------------------------------------------------------------------

DebugWaitExecutor::DebugWaitExecutor(const search::fef::IQueryEnvironment &env,
                                     const DebugWaitParams &params)
    : _params(params)
{
    (void)env;
}

void
DebugWaitExecutor::execute(search::fef::MatchData &data)
{
    FastOS_Time time;
    time.SetNow();
    double millis = _params.waitTime * 1000.0;

    while (time.MilliSecsToNow() < millis) {
        if (_params.busyWait) {
            for (int i = 0; i < 1000; i++)
                ;
        } else {
            int rem = (int)(millis - time.MilliSecsToNow());
            FastOS_Thread::Sleep(rem);
        }
    }
    *data.resolveFeature(outputs()[0]) = 1.0e-6 * time.MicroSecsToNow();
}

//-----------------------------------------------------------------------------

DebugWaitBlueprint::DebugWaitBlueprint()
    : Blueprint("debugWait"),
      _params()
{
}

void
DebugWaitBlueprint::visitDumpFeatures(const search::fef::IIndexEnvironment &env,
                                      search::fef::IDumpFeatureVisitor &visitor) const
{
    (void)env;
    (void)visitor;
}

search::fef::Blueprint::UP
DebugWaitBlueprint::createInstance() const
{
    return Blueprint::UP(new DebugWaitBlueprint());
}

bool
DebugWaitBlueprint::setup(const search::fef::IIndexEnvironment &env,
                          const search::fef::ParameterList &params)
{
    (void)env;
    _params.waitTime = params[0].asDouble();
    _params.busyWait = (params[1].asDouble() == 1.0);

    describeOutput("out", "actual time waited");
    return true;
}

search::fef::FeatureExecutor::LP
DebugWaitBlueprint::createExecutor(const search::fef::IQueryEnvironment &env) const
{
    return search::fef::FeatureExecutor::LP(new DebugWaitExecutor(env, _params));
}

//-----------------------------------------------------------------------------

} // namespace features
} // namespace search
