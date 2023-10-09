// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "constant_feature.h"
#include "valuefeature.h"
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/value_cache/constant_value.h>
#include <vespa/vespalib/util/stash.h>

#include <vespa/log/log.h>
LOG_SETUP(".features.constant_feature");

using namespace search::fef;

using vespalib::eval::ValueType;
using vespalib::eval::Function;
using vespalib::eval::SimpleConstantValue;

namespace search::features {

/**
 * Feature executor that returns a constant value.
 */
class ConstantFeatureExecutor : public fef::FeatureExecutor
{
private:
    const vespalib::eval::Value &_value;

public:
    ConstantFeatureExecutor(const vespalib::eval::Value &value)
        : _value(value)
    {}
    bool isPure() override { return true; }
    void execute(uint32_t) override {
        outputs().set_object(0, _value);
    }
    static FeatureExecutor &create(const vespalib::eval::Value &value, vespalib::Stash &stash) {
        return stash.create<ConstantFeatureExecutor>(value);
    }
};

ConstantBlueprint::ConstantBlueprint()
    : Blueprint("constant"),
      _key(),
      _value()
{
}

ConstantBlueprint::~ConstantBlueprint() = default;

void
ConstantBlueprint::visitDumpFeatures(const IIndexEnvironment &,
                                     IDumpFeatureVisitor &) const
{
}

Blueprint::UP
ConstantBlueprint::createInstance() const
{
    return std::make_unique<ConstantBlueprint>();
}

bool
ConstantBlueprint::setup(const IIndexEnvironment &env,
                         const ParameterList &params)
{
    _key = params[0].getValue();
    _value = env.getConstantValue(_key);
    if (!_value) {
        auto type_prop = env.getProperties().lookup(getName(), "type");
        auto value_prop = env.getProperties().lookup(getName(), "value");
        if ((type_prop.size() == 1) && (value_prop.size() == 1)) {
            auto type = ValueType::from_spec(type_prop.get());
            auto value = Function::parse(value_prop.get())->root().get_const_value();
            if (!type.is_error() && value && (value->type() == type)) {
                _value = std::make_unique<SimpleConstantValue>(std::move(value));
            } else {
                fail("Constant '%s' has invalid spec: type='%s', value='%s'",
                     _key.c_str(), type_prop.get().c_str(), value_prop.get().c_str());
            }
        } else {
            fail("Constant '%s' not found", _key.c_str());
        }
    } else if (_value->type().is_error()) {
        fail("Constant '%s' has invalid type", _key.c_str());
    }
    FeatureType output_type = _value
        ? FeatureType::object(_value->type())
        : FeatureType::number();
    describeOutput("out", "The constant looked up in index environment using the given key.",
                   output_type);
    return (_value && !_value->type().is_error());
}

FeatureExecutor &
ConstantBlueprint::createExecutor(const IQueryEnvironment &env, vespalib::Stash &stash) const
{
    (void) env;
    if (_value) {
        return ConstantFeatureExecutor::create(_value->value(), stash);
    } else {
        // Note: Should not happen, setup() has already failed
        return stash.create<SingleZeroValueExecutor>();
    }
}

}
