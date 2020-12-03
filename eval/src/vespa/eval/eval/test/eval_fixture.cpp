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
        ASSERT_TRUE(pos != param_repo.map.end());
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

void add_cell_values(TensorSpec &spec, TensorSpec::Address &addr,
                     const std::vector<std::pair<vespalib::string, size_t> > &dims,
                     size_t idx, size_t &seq, std::function<double(size_t)> gen)
{
    if (idx < dims.size()) {
        for (size_t i = 0; i < dims[idx].second; ++i) {
            addr.emplace(dims[idx].first, TensorSpec::Label(i)).first->second = TensorSpec::Label(i);
            add_cell_values(spec, addr, dims, idx + 1, seq, gen);
        }
    } else {
        spec.add(addr, gen(seq++));
    }
}

TensorSpec make_dense(const vespalib::string &type,
                      const std::vector<std::pair<vespalib::string, size_t> > &dims,
                      std::function<double(size_t)> gen)
{
    TensorSpec spec(type);
    TensorSpec::Address addr;
    size_t seq = 0;
    add_cell_values(spec, addr, dims, 0, seq, gen);
    return spec;
}

} // namespace vespalib::eval::test

ParamRepo &
ParamRepo::add(const vespalib::string &name, TensorSpec value_in, bool is_mutable_in) {
    ASSERT_TRUE(map.find(name) == map.end());
    map.insert_or_assign(name, Param(std::move(value_in), is_mutable_in));
    return *this;
}

ParamRepo &
EvalFixture::ParamRepo::add_vector(const char *d1, size_t s1, gen_fun_t gen)
{
    return add_dense({{d1, s1}}, gen);
}

ParamRepo &
EvalFixture::ParamRepo::add_matrix(const char *d1, size_t s1, const char *d2, size_t s2, gen_fun_t gen)
{
    return add_dense({{d1, s1}, {d2, s2}}, gen);
}

ParamRepo &
EvalFixture::ParamRepo::add_cube(const char *d1, size_t s1, const char *d2, size_t s2, const char *d3, size_t s3, gen_fun_t gen)
{
    return add_dense({{d1, s1}, {d2, s2}, {d3, s3}}, gen);
}

ParamRepo &
EvalFixture::ParamRepo::add_dense(const std::vector<std::pair<vespalib::string, size_t> > &dims, gen_fun_t gen)
{
    vespalib::string prev;
    vespalib::string name;
    vespalib::string type;
    for (const auto &dim: dims) {
        if (!prev.empty()) {
            ASSERT_LESS(prev, dim.first);
            type += ",";
        }
        name += fmt("%s%zu", dim.first.c_str(), dim.second);
        type += fmt("%s[%zu]", dim.first.c_str(), dim.second);
        prev = dim.first;
    }
    int cpy = 1;
    vespalib::string suffix = "";
    while (map.find(name + suffix) != map.end()) {
        suffix = fmt("$%d", ++cpy);
    }
    add(name + suffix, make_dense(fmt("tensor(%s)", type.c_str()), dims, gen));
    add(name + "f" + suffix, make_dense(fmt("tensor<float>(%s)", type.c_str()), dims, gen));
    add_mutable("@" + name + suffix, make_dense(fmt("tensor(%s)", type.c_str()), dims, gen));
    add_mutable("@" + name + "f" + suffix, make_dense(fmt("tensor<float>(%s)", type.c_str()), dims, gen));
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
