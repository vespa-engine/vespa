// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "debug_attribute_wait.h"
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/util/stash.h>


using search::attribute::IAttributeVector;
using namespace search::fef;

namespace search::features {

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

DebugAttributeWaitExecutor::DebugAttributeWaitExecutor(const IQueryEnvironment &,
                                                       const IAttributeVector *attribute,
                                                       const DebugAttributeWaitParams &params)
    : _attribute(attribute),
      _buf(),
      _params(params)
{ }

void
DebugAttributeWaitExecutor::execute(uint32_t docId)
{
    double waitTime = 0.0;

    if (_attribute != nullptr) {
        _buf.fill(*_attribute, docId);
        waitTime = _buf[0];
    }
    vespalib::Timer timer;
    vespalib::Timer::waitAtLeast(vespalib::from_s(waitTime), _params.busyWait);
    outputs().set_number(0, vespalib::to_s(timer.elapsed()));
}

//-----------------------------------------------------------------------------

DebugAttributeWaitBlueprint::DebugAttributeWaitBlueprint()
    : Blueprint("debugAttributeWait"),
      _params()
{
}

void
DebugAttributeWaitBlueprint::visitDumpFeatures(const IIndexEnvironment &, IDumpFeatureVisitor &) const
{
}

Blueprint::UP
DebugAttributeWaitBlueprint::createInstance() const
{
    return std::make_unique<DebugAttributeWaitBlueprint>();
}

bool
DebugAttributeWaitBlueprint::setup(const IIndexEnvironment &, const ParameterList &params)
{
    _attribute = params[0].getValue();
    _params.busyWait = (params[1].asDouble() == 1.0);

    describeOutput("out", "actual time waited");
    return true;
}

FeatureExecutor &
DebugAttributeWaitBlueprint::createExecutor(const IQueryEnvironment &env, vespalib::Stash &stash) const
{
    // Get attribute vector
    const IAttributeVector * attribute = env.getAttributeContext().getAttribute(_attribute);
    return stash.create<DebugAttributeWaitExecutor>(env, attribute, _params);
}

fef::ParameterDescriptions
DebugAttributeWaitBlueprint::getDescriptions() const
{
    return fef::ParameterDescriptions().desc().attribute(ParameterDataTypeSet::normalTypeSet(), ParameterCollection::ANY).number();
}

}
