// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include "eval_fixture.h"

namespace vespalib::eval::test {

using ParamRepo = EvalFixture::ParamRepo;

namespace {

NodeTypes get_types(const Function &function, const ParamRepo &param_repo) {
    std::vector<ValueType> param_types;
    for (size_t i = 0; i < function.num_params(); ++i) {
        auto pos = param_repo.map.find(function.param_name(i));
        ASSERT_TRUE(pos != param_repo.map.end());
        param_types.push_back(ValueType::from_spec(pos->second.type));
        ASSERT_TRUE(!param_types.back().is_error());
    }
    return NodeTypes(function, param_types);
}

const TensorFunction &make_tfun(bool optimized, const TensorEngine &engine, const Function &function,
                                const NodeTypes &node_types, Stash &stash)
{
    const TensorFunction &plain_fun = make_tensor_function(engine, function.root(), node_types, stash);
    return optimized ? engine.optimize(plain_fun, stash) : plain_fun;
}

std::vector<Value::UP> make_params(const TensorEngine &engine, const Function &function,
                                   const ParamRepo &param_repo)
{
    std::vector<Value::UP> result;
    for (size_t i = 0; i < function.num_params(); ++i) {
        auto pos = param_repo.map.find(function.param_name(i));
        ASSERT_TRUE(pos != param_repo.map.end());
        result.push_back(engine.from_spec(pos->second.value));
        ASSERT_TRUE(!result.back()->type().is_abstract());
    }
    return result;
}

std::vector<Value::CREF> get_refs(const std::vector<Value::UP> &values) {
    std::vector<Value::CREF> result;
    for (const auto &value: values) {
        result.emplace_back(*value);
    }
    return result;
}

} // namespace vespalib::eval::test

EvalFixture::EvalFixture(const TensorEngine &engine,
                         const vespalib::string &expr,
                         const ParamRepo &param_repo,
                         bool optimized)
    : _engine(engine),
      _stash(),
      _function(Function::parse(expr)),
      _node_types(get_types(_function, param_repo)),
      _tensor_function(make_tfun(optimized, _engine, _function, _node_types, _stash)),
      _ifun(_engine, _tensor_function),
      _ictx(_ifun),
      _param_values(make_params(_engine, _function, param_repo)),
      _params(get_refs(_param_values)),
      _result(_engine.to_spec(_ifun.eval(_ictx, _params)))
{
    auto result_type = ValueType::from_spec(_result.type());
    ASSERT_TRUE(!result_type.is_error());
}

} // namespace vespalib::eval::test
