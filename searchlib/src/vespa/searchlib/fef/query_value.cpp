// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "query_value.h"
#include "iindexenvironment.h"
#include "indexproperties.h"
#include "iqueryenvironment.h"
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/vespalib/locale/c.h>
#include <vespa/vespalib/util/issue.h>
#include <vespa/vespalib/util/string_hash.h>
#include <cerrno>

using document::TensorDataType;
using vespalib::Issue;
using vespalib::eval::DoubleValue;
using vespalib::eval::Function;
using vespalib::eval::InterpretedFunction;
using vespalib::eval::NodeTypes;
using vespalib::eval::SimpleObjectParams;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;
using vespalib::eval::ValueType;

using namespace search::fef::indexproperties;

namespace search::fef {

using ValueWrapper = AnyWrapper<Value::UP>;

InvalidValueTypeException::InvalidValueTypeException(const vespalib::string& query_key, const vespalib::string& type_str_in)
    : vespalib::Exception("Invalid type '" + type_str_in + "' for query value '" + query_key + "'"),
      _type_str(type_str_in)
{
}

InvalidTensorValueException::InvalidTensorValueException(const vespalib::eval::ValueType& type, const vespalib::string& expr_in)
    : vespalib::Exception("Could not create tensor value of type '" + type.to_spec() + "' from the expression '" + expr_in + "'"),
      _expr(expr_in)
{
}

namespace {

/**
 * Convert a string to a feature value using special quoting mechanics;
 * a string that can be converted directly into a feature
 * (numeric value) will be converted. If the string cannot be
 * converted directly, it will be hashed, after stripping the leading
 * "'" if it exists.
 */
feature_t
as_feature(const vespalib::string& str)
{
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
std::unique_ptr<Value>
empty_tensor(const ValueType& type)
{
    const auto& factory = vespalib::eval::FastValueBuilderFactory::get();
    return vespalib::eval::value_from_spec(TensorSpec(type.to_spec()), factory);
}

// Create a tensor value by evaluating a self-contained expression.
std::unique_ptr<Value>
as_tensor(const vespalib::string& expr, const ValueType& wanted_type)
{
    const auto& factory = vespalib::eval::FastValueBuilderFactory::get();
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

std::unique_ptr<Value>
decode_tensor_value(Property prop, const ValueType& value_type)
{
    if (prop.found() && !prop.get().empty()) {
        const vespalib::string& value = prop.get();
        vespalib::nbostream stream(value.data(), value.size());
        try {
            auto tensor = vespalib::eval::decode_value(stream, vespalib::eval::FastValueBuilderFactory::get());
            if (TensorDataType::isAssignableType(value_type, tensor->type())) {
                return tensor;
            } else {
                Issue::report("Query value type is '%s' but decoded tensor type is '%s'",
                              value_type.to_spec().c_str(), tensor->type().to_spec().c_str());
            }
        } catch (const vespalib::eval::DecodeValueException& e) {
            Issue::report("Query value has invalid binary format: %s", e.what());
        }
    }
    return {};
}

}

Property
QueryValue::config_lookup(const IIndexEnvironment& env) const
{
    const auto& props = env.getProperties();
    auto res = props.lookup(_name); // query(foo)
    if (!res.found()) {
        res = props.lookup(_old_key); // $foo
    }
    return res;
}

Property
QueryValue::request_lookup(const IQueryEnvironment& env) const
{
    const auto& props = env.getProperties();
    auto res = props.lookup(_name); // query(foo)
    if (!res.found()) {
        res = props.lookup(_key); // foo
    }
    if (!res.found()) {
        res = props.lookup(_old_key); // $foo
    }
    return res;
}

QueryValue::QueryValue()
    : _key(),
      _name(),
      _old_key(),
      _stored_value_key(),
      _type(ValueType::double_type())
{
}

QueryValue::QueryValue(const vespalib::string& key, const vespalib::eval::ValueType& type)
    : _key(key),
      _name("query(" + key + ")"),
      _old_key("$" + key),
      _stored_value_key("query.value." + key),
      _type(type)
{
}

QueryValue::~QueryValue() = default;

QueryValue
QueryValue::from_config(const vespalib::string& key, const IIndexEnvironment& env)
{
    vespalib::string type_str = type::QueryFeature::lookup(env.getProperties(), key);
    ValueType type = type_str.empty() ? ValueType::double_type() : ValueType::from_spec(type_str);
    if (type.is_error()) {
        throw InvalidValueTypeException(key, type_str);
    }
    return {key, type};
}

std::unique_ptr<Value>
QueryValue::make_default_value(const IIndexEnvironment& env) const
{
    Property p = config_lookup(env);
    if (_type.is_double()) {
        if (p.found()) {
            return std::make_unique<DoubleValue>(as_feature(p.get()));
        } else {
            return std::make_unique<DoubleValue>(0);
        }
    } else {
        if (p.found()) {
            auto tensor = as_tensor(p.get(), _type);
            if (tensor.get() == nullptr) {
                throw InvalidTensorValueException(_type, p.get().c_str());
            }
            return tensor;
        } else {
            return empty_tensor(_type);
        }
    }
}

void
QueryValue::prepare_shared_state(const fef::IQueryEnvironment& env, fef::IObjectStore& store) const
{
    if (!_stored_value_key.empty() && _type.has_dimensions() && (store.get(_stored_value_key) == nullptr)) {
        if (auto value = decode_tensor_value(request_lookup(env), _type)) {
            store.add(_stored_value_key, std::make_unique<ValueWrapper>(std::move(value)));
        }
    }
}

const Value*
QueryValue::lookup_value(const fef::IObjectStore& store) const
{
    if (const Anything* wrapped_value = store.get(_stored_value_key)) {
        return ValueWrapper::getValue(*wrapped_value).get();
    }
    return nullptr;
}

double
QueryValue::lookup_number(const fef::IQueryEnvironment& env, double default_value) const
{
    assert(!_type.has_dimensions());
    auto p = request_lookup(env);
    if (p.found()) {
        return as_feature(p.get());
    }
    return default_value;
}

}

