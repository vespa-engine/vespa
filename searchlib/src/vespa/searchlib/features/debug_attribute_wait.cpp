// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "debug_attribute_wait.h"
#include <thread>

using search::attribute::IAttributeVector;

namespace search {

using namespace fef;

namespace features {

//-----------------------------------------------------------------------------

class DebugAttributeWaitExecutor : public FeatureExecutor
{
private:
    const IAttributeVector *_attribute;
    attribute::FloatContent  _buf;
    DebugAttributeWaitParams _params;

public:
    DebugAttributeWaitExecutor(const IQueryEnvironment &env,
                               const IAttributeVector * attribute,
                               const DebugAttributeWaitParams &params);
    void execute(uint32_t docId) override;
};

DebugAttributeWaitExecutor::DebugAttributeWaitExecutor(const IQueryEnvironment &env,
                                                       const IAttributeVector *attribute,
                                                       const DebugAttributeWaitParams &params)
    : _attribute(attribute),
      _buf(),
      _params(params)
{
    (void)env;
}

void
DebugAttributeWaitExecutor::execute(uint32_t docId)
{
    double waitTime = 0.0;
    FastOS_Time time;
    time.SetNow();

    if (_attribute != NULL) {
        _buf.fill(*_attribute, docId);
        waitTime = _buf[0];
    }
    double millis = waitTime * 1000.0;

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

DebugAttributeWaitBlueprint::DebugAttributeWaitBlueprint()
    : Blueprint("debugAttributeWait"),
      _params()
{
}

void
DebugAttributeWaitBlueprint::visitDumpFeatures(const IIndexEnvironment &env, IDumpFeatureVisitor &visitor) const
{
    (void)env;
    (void)visitor;
}

Blueprint::UP
DebugAttributeWaitBlueprint::createInstance() const
{
    return Blueprint::UP(new DebugAttributeWaitBlueprint());
}

bool
DebugAttributeWaitBlueprint::setup(const IIndexEnvironment &env, const ParameterList &params)
{
    (void)env;
    _attribute = params[0].getValue();
    _params.busyWait = (params[1].asDouble() == 1.0);

    describeOutput("out", "actual time waited");
    env.hintAttributeAccess(_attribute);
    return true;
}

FeatureExecutor &
DebugAttributeWaitBlueprint::createExecutor(const IQueryEnvironment &env, vespalib::Stash &stash) const
{
    // Get attribute vector
    const IAttributeVector * attribute = env.getAttributeContext().getAttribute(_attribute);
    return stash.create<DebugAttributeWaitExecutor>(env, attribute, _params);
}

//-----------------------------------------------------------------------------

} // namespace features
} // namespace search
