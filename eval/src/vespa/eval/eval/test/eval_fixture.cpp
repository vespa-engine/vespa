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

std::set<size_t> get_mutable(const Function &function, const ParamRepo &param_repo) {
    std::set<size_t> mutable_set;
    for (size_t i = 0; i < function.num_params(); ++i) {
        auto pos = param_repo.map.find(function.param_name(i));
        ASSERT_TRUE(pos != param_repo.map.end());
        if (pos->second.is_mutable) {
            mutable_set.insert(i);
        }
    }
    return mutable_set;
}

struct MyMutableInject : public tensor_function::Inject {
    MyMutableInject(const ValueType &result_type_in, size_t param_idx_in)
        : Inject(result_type_in, param_idx_in) {}
    bool result_is_mutable() const override { return true; }
};

const TensorFunction &maybe_patch(bool allow_mutable, const TensorFunction &plain_fun, const std::set<size_t> &mutable_set, Stash &stash) {
    using Child = TensorFunction::Child;
    if (!allow_mutable) {
        return plain_fun;
    }
    Child root(plain_fun);
    std::vector<Child::CREF> nodes({root});
    for (size_t i = 0; i < nodes.size(); ++i) {
        nodes[i].get().get().push_children(nodes);
    }
    while (!nodes.empty()) {
        const Child &child = nodes.back();
        if (auto inject = as<tensor_function::Inject>(child.get())) {
            if (mutable_set.count(inject->param_idx()) > 0) {
                child.set(stash.create<MyMutableInject>(inject->result_type(), inject->param_idx()));
            }
        }
        nodes.pop_back();
    }
    return root.get();
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
                         bool optimized,
                         bool allow_mutable)
    : _engine(engine),
      _stash(),
      _function(Function::parse(expr)),
      _node_types(get_types(_function, param_repo)),
      _mutable_set(get_mutable(_function, param_repo)),
      _plain_tensor_function(make_tensor_function(_engine, _function.root(), _node_types, _stash)),
      _patched_tensor_function(maybe_patch(allow_mutable, _plain_tensor_function, _mutable_set, _stash)),
      _tensor_function(optimized ? _engine.optimize(_patched_tensor_function, _stash) : _patched_tensor_function),
      _ifun(_engine, _tensor_function),
      _ictx(_ifun),
      _param_values(make_params(_engine, _function, param_repo)),
      _params(get_refs(_param_values)),
      _result(_engine.to_spec(_ifun.eval(_ictx, _params)))
{
    auto result_type = ValueType::from_spec(_result.type());
    ASSERT_TRUE(!result_type.is_error());
}

const TensorSpec
EvalFixture::get_param(size_t idx) const
{
    ASSERT_LESS(idx, _param_values.size());
    return _engine.to_spec(*(_param_values[idx]));
}

size_t
EvalFixture::num_params() const
{
    return _param_values.size();
}

} // namespace vespalib::eval::test
