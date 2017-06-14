// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "debug_wait.h"
#include <thread>

namespace search {

using namespace fef;

namespace features {

//-----------------------------------------------------------------------------

class DebugWaitExecutor : public FeatureExecutor
{
private:
    DebugWaitParams _params;

public:
    DebugWaitExecutor(const IQueryEnvironment &env, const DebugWaitParams &params);
    void execute(uint32_t docId) override;
};

DebugWaitExecutor::DebugWaitExecutor(const IQueryEnvironment &env, const DebugWaitParams &params)
    : _params(params)
{
    (void)env;
}

void
DebugWaitExecutor::execute(uint32_t)
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
            std::this_thread::sleep_for(std::chrono::milliseconds(rem));
        }
    }
    outputs().set_number(0, 1.0e-6 * time.MicroSecsToNow());
}

//-----------------------------------------------------------------------------

DebugWaitBlueprint::DebugWaitBlueprint()
    : Blueprint("debugWait"),
      _params()
{
}

void
DebugWaitBlueprint::visitDumpFeatures(const IIndexEnvironment &env, IDumpFeatureVisitor &visitor) const
{
    (void)env;
    (void)visitor;
}

Blueprint::UP
DebugWaitBlueprint::createInstance() const
{
    return Blueprint::UP(new DebugWaitBlueprint());
}

bool
DebugWaitBlueprint::setup(const IIndexEnvironment &env, const ParameterList &params)
{
    (void)env;
    _params.waitTime = params[0].asDouble();
    _params.busyWait = (params[1].asDouble() == 1.0);

    describeOutput("out", "actual time waited");
    return true;
}

FeatureExecutor &
DebugWaitBlueprint::createExecutor(const IQueryEnvironment &env, vespalib::Stash &stash) const
{
    return stash.create<DebugWaitExecutor>(env, _params);
}

//-----------------------------------------------------------------------------

} // namespace features
} // namespace search
