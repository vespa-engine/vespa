// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include "eval_fixture.h"
#include "reference_evaluation.h"
#include <vespa/eval/eval/make_tensor_function.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/optimize_tensor_function.h>
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string_short::fmt;

namespace vespalib::eval::test {

using ParamRepo = EvalFixture::ParamRepo;

namespace {

std::shared_ptr<Function const> verify_function(std::shared_ptr<Function const> fun) {
    if (fun->has_error()) {
        fprintf(stderr, "eval_fixture: function parse failed: %s\n", fun->get_error().c_str());
    }
    ASSERT_TRUE(!fun->has_error());
    return fun;
}

NodeTypes get_types(const Function &function, const ParamRepo &param_repo) {
    std::vector<ValueType> param_types;
    for (size_t i = 0; i < function.num_params(); ++i) {
        auto pos = param_repo.map.find(function.param_name(i));
        if (pos == param_repo.map.end()) {
            TEST_STATE(fmt("param name: '%s'", function.param_name(i).data()).c_str());
            ASSERT_TRUE(pos != param_repo.map.end());
        }
        param_types.push_back(ValueType::from_spec(pos->second.value.type()));
        ASSERT_TRUE(!param_types.back().is_error());
    }
    NodeTypes node_types(function, param_types);
    if (!node_types.errors().empty()) {
        for (const auto &msg: node_types.errors()) {
            fprintf(stderr, "eval_fixture: type error: %s\n", msg.c_str());
        }
    }
    ASSERT_TRUE(node_types.errors().empty());
    return node_types;
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

std::vector<Value::UP> make_params(const ValueBuilderFactory &factory, const Function &function,
                                   const ParamRepo &param_repo)
{
    std::vector<Value::UP> result;
    for (size_t i = 0; i < function.num_params(); ++i) {
        auto pos = param_repo.map.find(function.param_name(i));
        ASSERT_TRUE(pos != param_repo.map.end());
        result.push_back(value_from_spec(pos->second.value, factory));
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

ParamRepo &
EvalFixture::ParamRepo::add(const vespalib::string &name, TensorSpec value)
{
    ASSERT_TRUE(map.find(name) == map.end());
    map.insert_or_assign(name, Param(std::move(value), false));
    return *this;
}

ParamRepo &
EvalFixture::ParamRepo::add_mutable(const vespalib::string &name, TensorSpec value)
{
    ASSERT_TRUE(map.find(name) == map.end());
    map.insert_or_assign(name, Param(std::move(value), true));
    return *this;
}

// produce 4 variants: float/double * mutable/const
EvalFixture::ParamRepo &
EvalFixture::ParamRepo::add_variants(const vespalib::string &name_base,
                                     const GenSpec &spec)
{
    auto name_f = name_base + "_f";
    auto name_m = "@" + name_base;
    auto name_m_f = "@" + name_base + "_f";
    auto dbl_gs = spec.cpy().cells_double();
    auto flt_gs = spec.cpy().cells_float();
    add(name_base, dbl_gs);
    add(name_f, flt_gs);
    add_mutable(name_m, dbl_gs);
    add_mutable(name_m_f, flt_gs);
    return *this;
}

void
EvalFixture::detect_param_tampering(const ParamRepo &param_repo, bool allow_mutable) const
{
    for (size_t i = 0; i < _function->num_params(); ++i) {
        auto pos = param_repo.map.find(_function->param_name(i));
        ASSERT_TRUE(pos != param_repo.map.end());
        bool allow_tampering = allow_mutable && pos->second.is_mutable;
        if (!allow_tampering) {
            ASSERT_EQUAL(pos->second.value, spec_from_value(*_param_values[i]));
        }
    }
}

EvalFixture::EvalFixture(const ValueBuilderFactory &factory,
                         const vespalib::string &expr,
                         const ParamRepo &param_repo,
                         bool optimized,
                         bool allow_mutable)
    : _factory(factory),
      _stash(),
      _function(verify_function(Function::parse(expr))),
      _node_types(get_types(*_function, param_repo)),
      _mutable_set(get_mutable(*_function, param_repo)),
      _plain_tensor_function(make_tensor_function(_factory, _function->root(), _node_types, _stash)),
      _patched_tensor_function(maybe_patch(allow_mutable, _plain_tensor_function, _mutable_set, _stash)),
      _tensor_function(optimized ? optimize_tensor_function(_factory, _patched_tensor_function, _stash) : _patched_tensor_function),
      _ifun(_factory, _tensor_function),
      _ictx(_ifun),
      _param_values(make_params(_factory, *_function, param_repo)),
      _params(get_refs(_param_values)),
      _result(spec_from_value(_ifun.eval(_ictx, _params)))
{
    auto result_type = ValueType::from_spec(_result.type());
    ASSERT_TRUE(!result_type.is_error());
    TEST_DO(detect_param_tampering(param_repo, allow_mutable));
}

const TensorSpec
EvalFixture::get_param(size_t idx) const
{
    ASSERT_LESS(idx, _param_values.size());
    return spec_from_value(*(_param_values[idx]));
}

size_t
EvalFixture::num_params() const
{
    return _param_values.size();
}

TensorSpec
EvalFixture::ref(const vespalib::string &expr, const ParamRepo &param_repo)
{
    auto fun = Function::parse(expr);
    std::vector<TensorSpec> params;
    for (size_t i = 0; i < fun->num_params(); ++i) {
        auto pos = param_repo.map.find(fun->param_name(i));
        ASSERT_TRUE(pos != param_repo.map.end());
        params.push_back(pos->second.value);
    }
    return ReferenceEvaluation::eval(*fun, params);
}

} // namespace vespalib::eval::test
