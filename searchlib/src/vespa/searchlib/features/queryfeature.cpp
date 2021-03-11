// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "queryfeature.h"
#include "utils.h"
#include "valuefeature.h"
#include "constant_tensor_executor.h"

#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/feature_type.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/vespalib/locale/c.h>
#include <cerrno>

#include <vespa/log/log.h>
LOG_SETUP(".features.queryfeature");

using namespace search::fef;
using namespace search::fef::indexproperties;
using document::TensorDataType;
using vespalib::eval::ValueType;
using search::fef::FeatureType;

namespace search::features {

namespace {

/**
 * Convert a string to a feature value using special quoting
 * mechanics; a string that can be converted directly into a feature
 * (numeric value) will be converted. If the string cannot be
 * converted directly, it will be hashed, after stripping the leading
 * "'" if it exists.
 *
 * @return feature value
 * @param str string value to be converted
 **/
feature_t asFeature(const vespalib::string &str) {
    char *end;
    errno = 0;
    double val = vespalib::locale::c::strtod(str.c_str(), &end);
    if (errno != 0 || *end != '\0') { // not happy
        if (str.size() > 0 && str[0] == '\'') {
            val = vespalib::hash_code(str.substr(1));
        } else {
            val = vespalib::hash_code(str);
        }
    }
    return val;
}

} // namespace search::features::<unnamed>

QueryBlueprint::QueryBlueprint() :
    Blueprint("query"),
    _key(),
    _key2(),
    _defaultValue(0),
    _valueType(ValueType::double_type())
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
    _key = params[0].getValue();
    _key2 = "$";
    _key2.append(_key);

    vespalib::string key3;
    key3.append("query(");
    key3.append(_key);
    key3.append(")");
    Property p = env.getProperties().lookup(key3);
    if (!p.found()) {
        p = env.getProperties().lookup(_key2);
    }
    if (p.found()) {
        _defaultValue = asFeature(p.get());
    }
    vespalib::string queryFeatureType = type::QueryFeature::lookup(env.getProperties(), _key);
    if (!queryFeatureType.empty()) {
        _valueType = ValueType::from_spec(queryFeatureType);
        if (_valueType.is_error()) {
            LOG(error, "%s: invalid type: '%s'", getName().c_str(), queryFeatureType.c_str());
        }
    }
    FeatureType output_type = _valueType.is_double()
                              ? FeatureType::number()
                              : FeatureType::object(_valueType);
    describeOutput("out", "The value looked up in query properties using the given key.", output_type);
    return !_valueType.is_error();
}

namespace {

FeatureExecutor &
createTensorExecutor(const IQueryEnvironment &env,
                     const vespalib::string &queryKey,
                     const ValueType &valueType, vespalib::Stash &stash)
{
    Property prop = env.getProperties().lookup(queryKey);
    if (prop.found() && !prop.get().empty()) {
        const vespalib::string &value = prop.get();
        vespalib::nbostream stream(value.data(), value.size());
        auto tensor = vespalib::eval::decode_value(stream, vespalib::eval::FastValueBuilderFactory::get());
        if (!TensorDataType::isAssignableType(valueType, tensor->type())) {
            LOG(warning, "Query feature type is '%s' but other tensor type is '%s'",
                valueType.to_spec().c_str(), tensor->type().to_spec().c_str());
            return ConstantTensorExecutor::createEmpty(valueType, stash);
        }
        return ConstantTensorExecutor::create(std::move(tensor), stash);
    }
    return ConstantTensorExecutor::createEmpty(valueType, stash);
}

}

FeatureExecutor &
QueryBlueprint::createExecutor(const IQueryEnvironment &env, vespalib::Stash &stash) const
{
    if (_valueType.has_dimensions()) {
        return createTensorExecutor(env, _key, _valueType, stash);
    } else {
        std::vector<feature_t> values;
        Property p = env.getProperties().lookup(_key);
        if (!p.found()) {
            p = env.getProperties().lookup(_key2);
        }
        if (p.found()) {
            return stash.create<SingleValueExecutor>(asFeature(p.get()));
        } else {
            return stash.create<SingleValueExecutor>(_defaultValue);
        }
    }
}

}
