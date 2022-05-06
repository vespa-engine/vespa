// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
#include <vespa/vespalib/util/issue.h>
#include <cerrno>

#include <vespa/log/log.h>
LOG_SETUP(".features.queryfeature");

using namespace search::fef;
using namespace search::fef::indexproperties;
using document::TensorDataType;
using vespalib::eval::ValueType;
using vespalib::eval::Value;
using vespalib::Issue;
using search::fef::FeatureType;
using search::fef::AnyWrapper;
using search::fef::Anything;

using ValueWrapper = AnyWrapper<Value::UP>;

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

// query(foo):
// query.value.foo -> decoded tensor value 'foo'
vespalib::string make_value_key(const vespalib::string &base, const vespalib::string &sub_key) {
    vespalib::string key(base);
    key.append(".value.");
    key.append(sub_key);
    return key;
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
    _stored_value_key = make_value_key(getBaseName(), _key);

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

Value::UP make_tensor_value(const IQueryEnvironment &env,
                            const vespalib::string &queryKey,
                            const ValueType &valueType)
{
    Property prop = env.getProperties().lookup(queryKey);
    if (prop.found() && !prop.get().empty()) {
        const vespalib::string &value = prop.get();
        vespalib::nbostream stream(value.data(), value.size());
        try {
            auto tensor = vespalib::eval::decode_value(stream, vespalib::eval::FastValueBuilderFactory::get());
            if (TensorDataType::isAssignableType(valueType, tensor->type())) {
                return tensor;
            } else {
                Issue::report("Query feature type is '%s' but other tensor type is '%s'",
                              valueType.to_spec().c_str(), tensor->type().to_spec().c_str());
            }
        } catch (const vespalib::eval::DecodeValueException &e) {
            Issue::report("Query feature has invalid binary format: %s", e.what());
        }
    }
    return {};
}

}

void
QueryBlueprint::prepareSharedState(const fef::IQueryEnvironment &env, fef::IObjectStore &store) const
{
    if (!_stored_value_key.empty() && (store.get(_stored_value_key) == nullptr)) {
        auto value = make_tensor_value(env, _key, _valueType);
        if (value) {
            store.add(_stored_value_key, std::make_unique<ValueWrapper>(std::move(value)));
        }
    }
}

FeatureExecutor &
QueryBlueprint::createExecutor(const IQueryEnvironment &env, vespalib::Stash &stash) const
{
    if (_valueType.has_dimensions()) {
        if (const Anything *wrapped_value = env.getObjectStore().get(_stored_value_key)) {
            if (const Value *value = ValueWrapper::getValue(*wrapped_value).get()) {
                return stash.create<ConstantTensorRefExecutor>(*value);
            }
        }
        return ConstantTensorExecutor::createEmpty(_valueType, stash);
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
