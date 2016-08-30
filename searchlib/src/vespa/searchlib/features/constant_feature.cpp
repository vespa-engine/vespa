// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".features.constant_feature");
#include "constant_feature.h"

#include <vespa/searchlib/fef/featureexecutor.h>
#include "valuefeature.h"
#include <vespa/vespalib/eval/value_cache/constant_value.h>

using namespace search::fef;

namespace search {
namespace features {

/**
 * Feature executor that returns a constant tensor.
 */
class ConstantFeatureExecutor : public fef::FeatureExecutor
{
private:
    const vespalib::eval::Value &_value;

public:
    ConstantFeatureExecutor(const vespalib::eval::Value &value)
        : _value(value)
    {}
    virtual bool isPure() override { return true; }
    virtual void execute(fef::MatchData &data) override {
        *data.resolve_object_feature(outputs()[0]) = _value;
    }
    static FeatureExecutor::LP create(const vespalib::eval::Value &value) {
        return FeatureExecutor::LP(new ConstantFeatureExecutor(value));
    }
};

ConstantBlueprint::ConstantBlueprint()
    : Blueprint("constant"),
      _key(),
      _value()
{
}

ConstantBlueprint::~ConstantBlueprint()
{
}

void
ConstantBlueprint::visitDumpFeatures(const IIndexEnvironment &,
                                     IDumpFeatureVisitor &) const
{
}

Blueprint::UP
ConstantBlueprint::createInstance() const
{
    return Blueprint::UP(new ConstantBlueprint());
}

bool
ConstantBlueprint::setup(const IIndexEnvironment &env,
                         const ParameterList &params)
{
    _key = params[0].getValue();
    _value = env.getConstantValue(_key);
    if (!_value) {
        LOG(error, "Constant '%s' not found", _key.c_str());
    }
    FeatureType output_type = _value ?
                              FeatureType::object(_value->type()) :
                              FeatureType::number();
    describeOutput("out", "The constant looked up in index environment using the given key.",
                   output_type);
    return static_cast<bool>(_value);
}

FeatureExecutor::LP
ConstantBlueprint::createExecutor(const IQueryEnvironment &env) const
{
    (void) env;
    if (_value) {
        return ConstantFeatureExecutor::create(_value->value());
    } else {
        // Note: Should not happen, setup() has already failed
        return FeatureExecutor::LP(new SingleZeroValueExecutor());
    }
}

} // namespace features
} // namespace search
