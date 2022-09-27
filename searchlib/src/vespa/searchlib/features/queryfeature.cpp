// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "queryfeature.h"
#include "constant_tensor_executor.h"
#include "utils.h"
#include "valuefeature.h"
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/fef/feature_type.h>
#include <vespa/eval/eval/value_type.h>

#include <vespa/log/log.h>
LOG_SETUP(".features.queryfeature");

using namespace search::fef;
using search::fef::FeatureType;

namespace search::features {

QueryBlueprint::QueryBlueprint()
  : Blueprint("query"),
    _qvalue(),
    _default_object_value()
{
}

QueryBlueprint::~QueryBlueprint() = default;

void
QueryBlueprint::visitDumpFeatures(const IIndexEnvironment &, IDumpFeatureVisitor &) const
{
}

Blueprint::UP
QueryBlueprint::createInstance() const
{
    return std::make_unique<QueryBlueprint>();
}

bool
QueryBlueprint::setup(const IIndexEnvironment &env, const ParameterList &params)
{
    try {
        _qvalue = QueryValue::from_config(params[0].getValue(), env);
        _default_object_value = _qvalue.make_default_value(env);
    } catch (const InvalidValueTypeException& ex) {
        return fail("invalid type: '%s'", ex.type_str().c_str());
    } catch (const InvalidTensorValueException& ex) {
        return fail("could not create default tensor value of type '%s' from the expression '%s'",
                    _qvalue.type().to_spec().c_str(), ex.expr().c_str());
    }
    const auto& type = _qvalue.type();
    FeatureType output_type = type.is_double() ? FeatureType::number() : FeatureType::object(type);
    describeOutput("out", "The value looked up in query properties using the given key.", output_type);
    return true;
}

void
QueryBlueprint::prepareSharedState(const fef::IQueryEnvironment &env, fef::IObjectStore &store) const
{
    _qvalue.prepare_shared_state(env, store);
}

FeatureExecutor &
QueryBlueprint::createExecutor(const IQueryEnvironment &env, vespalib::Stash &stash) const
{
    if (_qvalue.type().has_dimensions()) {
        if (const vespalib::eval::Value *value = _qvalue.lookup_value(env.getObjectStore())) {
            return stash.create<ConstantTensorRefExecutor>(*value);
        }
        return stash.create<ConstantTensorRefExecutor>(*_default_object_value);
    } else {
        return stash.create<SingleValueExecutor>(_qvalue.lookup_number(env, _default_object_value->as_double()));
    }
}

}
