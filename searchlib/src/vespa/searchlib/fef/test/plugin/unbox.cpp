// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "unbox.h"
#include <vespa/vespalib/util/stash.h>

namespace search::fef::test {

namespace {

struct UnboxExecutor : FeatureExecutor {
    bool isPure() override { return true; }
    void execute(uint32_t) override {
        outputs().set_number(0, inputs().get_object(0).get().as_double());
    }
};

struct ForwardExecutor : FeatureExecutor {
    bool isPure() override { return true; }
    void execute(uint32_t) override {
        outputs().set_number(0, inputs().get_number(0));
    }
};

} // namespace search::fef::test::<unnamed>

UnboxBlueprint::UnboxBlueprint()
  : Blueprint("unbox"),
    _was_object(false)
{
}

void
UnboxBlueprint::visitDumpFeatures(const IIndexEnvironment &, IDumpFeatureVisitor &) const
{
}

Blueprint::UP
UnboxBlueprint::createInstance() const
{
    return std::make_unique<UnboxBlueprint>();
}

ParameterDescriptions
UnboxBlueprint::getDescriptions() const
{
    return fef::ParameterDescriptions().desc().feature();
}

bool
UnboxBlueprint::setup(const IIndexEnvironment &, const ParameterList &params)
{
    if (auto input = defineInput(params[0].getValue(), AcceptInput::ANY)) {
        _was_object = input.value().is_object();
        describeOutput("value", "unboxed value", FeatureType::number());
        return true;
    }
    return false; // dependency error
}

FeatureExecutor &
UnboxBlueprint::createExecutor(const IQueryEnvironment &, vespalib::Stash &stash) const
{
    if (_was_object) {
        return stash.create<UnboxExecutor>();
    } else {
        return stash.create<ForwardExecutor>();
    }
}

} // namespace search::fef::test
