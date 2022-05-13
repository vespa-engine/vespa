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
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/interpreted_function.h>
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
using vespalib::eval::TensorSpec;
using vespalib::eval::Function;
using vespalib::eval::InterpretedFunction;
using vespalib::eval::NodeTypes;
using vespalib::eval::SimpleObjectParams;
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

// Create an empty tensor of the given type.
Value::UP empty_tensor(const ValueType &type) {
    const auto &factory = vespalib::eval::FastValueBuilderFactory::get();
    return vespalib::eval::value_from_spec(TensorSpec(type.to_spec()), factory);
}

// Create a tensor value by evaluating a self-contained expression.
Value::UP as_tensor(const vespalib::string &expr, const ValueType &wanted_type) {
    const auto &factory = vespalib::eval::FastValueBuilderFactory::get();
    auto fun = Function::parse(expr);
    if (!fun->has_error() && (fun->num_params() == 0)) {
        NodeTypes types = NodeTypes(*fun, {});
        ValueType res_type = types.get_type(fun->root());
        if (res_type == wanted_type) {
            SimpleObjectParams params({});
            InterpretedFunction ifun(factory, *fun, types);
            InterpretedFunction::Context ctx(ifun);
            return factory.copy(ifun.eval(ctx, params));
        }
    }
    return {};
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

Property
QueryBlueprint::config_lookup(const IIndexEnvironment &env) const
{
    const auto &props = env.getProperties();
    auto res = props.lookup(getName()); // query(foo)
    if (!res.found()) {
        res = props.lookup(_old_key); // $foo
    }
    return res;
}

Property
QueryBlueprint::request_lookup(const IQueryEnvironment &env) const
{
    const auto &props = env.getProperties();
    auto res = props.lookup(getName()); // query(foo)
    if (!res.found()) {
        res = props.lookup(_key); // foo
    }
    if (!res.found()) {
        res = props.lookup(_old_key); // $foo
    }
    return res;
}

QueryBlueprint::QueryBlueprint()
  : Blueprint("query"),
    _key(),
    _old_key(),
    _stored_value_key(),
    _type(ValueType::double_type()),
    _default_number_value(),
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
    _key = params[0].getValue();
    _old_key = "$";
    _old_key.append(_key);
    _stored_value_key = make_value_key(getBaseName(), _key);
    vespalib::string type_str = type::QueryFeature::lookup(env.getProperties(), _key);
    if (!type_str.empty()) {
        _type = ValueType::from_spec(type_str);
        if (_type.is_error()) {
            return fail("invalid type: '%s'", type_str.c_str());
        }
    }
    Property p = config_lookup(env);
    if (_type.is_double()) {
        if (p.found()) {
            _default_number_value = asFeature(p.get());
        }
    } else {
        if (p.found()) {
            _default_object_value = as_tensor(p.get(), _type);
            if (_default_object_value.get() == nullptr) {
                return fail("could not create default tensor value of type '%s' from the expression '%s'",
                            _type.to_spec().c_str(), p.get().c_str());
            }
        } else {
            _default_object_value = empty_tensor(_type);
        }
    }
    FeatureType output_type = _type.is_double() ? FeatureType::number() : FeatureType::object(_type);
    describeOutput("out", "The value looked up in query properties using the given key.", output_type);
    assert(_type.has_dimensions() == (_default_object_value.get() != nullptr));
    return true;
}

namespace {

Value::UP decode_tensor_value(Property prop, const ValueType &valueType) {
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
    if (!_stored_value_key.empty() && _type.has_dimensions() && (store.get(_stored_value_key) == nullptr)) {
        if (auto value = decode_tensor_value(request_lookup(env), _type)) {
            store.add(_stored_value_key, std::make_unique<ValueWrapper>(std::move(value)));
        }
    }
}

FeatureExecutor &
QueryBlueprint::createExecutor(const IQueryEnvironment &env, vespalib::Stash &stash) const
{
    if (_type.has_dimensions()) {
        if (const Anything *wrapped_value = env.getObjectStore().get(_stored_value_key)) {
            if (const Value *value = ValueWrapper::getValue(*wrapped_value).get()) {
                return stash.create<ConstantTensorRefExecutor>(*value);
            }
        }
        return stash.create<ConstantTensorRefExecutor>(*_default_object_value);
    } else {
        auto p = request_lookup(env);
        if (p.found()) {
            return stash.create<SingleValueExecutor>(asFeature(p.get()));
        } else {
            return stash.create<SingleValueExecutor>(_default_number_value);
        }
    }
}

}
