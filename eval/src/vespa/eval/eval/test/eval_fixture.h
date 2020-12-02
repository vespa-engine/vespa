// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/vespalib/util/stash.h>
#include <set>
#include <functional>

namespace vespalib::eval::test {

class EvalFixture
{
public:
    struct Param {
        TensorSpec value; // actual parameter value
        bool is_mutable; // input will be mutable (if allow_mutable is true)
        Param(TensorSpec value_in, bool is_mutable_in)
            : value(std::move(value_in)), is_mutable(is_mutable_in) {}
        ~Param() {}
    };

    struct ParamRepo {
        std::map<vespalib::string,Param> map;
        using gen_fun_t = std::function<double(size_t)>;
        static double gen_N(size_t seq) { return (seq + 1); }
        ParamRepo() : map() {}
        ParamRepo &add(const vespalib::string &name, TensorSpec value_in, bool is_mutable_in);
        ParamRepo &add(const vespalib::string &name, const TensorSpec &value) {
            return add(name, value, false);
        }
        ParamRepo &add_mutable(const vespalib::string &name, const TensorSpec &value) {
            return add(name, value, true);
        }
        ParamRepo &add_vector(const char *d1, size_t s1, gen_fun_t = gen_N);
        ParamRepo &add_matrix(const char *d1, size_t s1, const char *d2, size_t s2, gen_fun_t gen = gen_N);
        ParamRepo &add_cube(const char *d1, size_t s1, const char *d2, size_t s2, const char *d3, size_t s3, gen_fun_t gen = gen_N);
        ParamRepo &add_dense(const std::vector<std::pair<vespalib::string, size_t> > &dims, gen_fun_t gen = gen_N);
        ~ParamRepo() {}
    };

private:
    EngineOrFactory                 _engine;
    Stash                           _stash;
    std::shared_ptr<Function const> _function;
    NodeTypes                       _node_types;
    std::set<size_t>                _mutable_set;
    const TensorFunction           &_plain_tensor_function;
    const TensorFunction           &_patched_tensor_function;
    const TensorFunction           &_tensor_function;
    InterpretedFunction             _ifun;
    InterpretedFunction::Context    _ictx;
    std::vector<Value::UP>          _param_values;
    SimpleObjectParams              _params;
    TensorSpec                      _result;

    template <typename T>
    void find_all(const TensorFunction &node, std::vector<const T *> &list) {
        if (auto self = as<T>(node)) {
            list.push_back(self);
        }
        std::vector<TensorFunction::Child::CREF> children;
        node.push_children(children);
        for (const auto &child: children) {
            find_all(child.get().get(), list);
        }
    }

    void detect_param_tampering(const ParamRepo &param_repo, bool allow_mutable) const;

public:
    EvalFixture(EngineOrFactory engine, const vespalib::string &expr, const ParamRepo &param_repo,
                bool optimized = true, bool allow_mutable = false);
    ~EvalFixture() {}
    template <typename T>
    std::vector<const T *> find_all() {
        std::vector<const T *> list;
        find_all(_tensor_function, list);
        return list;
    }
    const TensorSpec &result() const { return _result; }
    const TensorSpec get_param(size_t idx) const;
    size_t num_params() const;
    static TensorSpec ref(const vespalib::string &expr, const ParamRepo &param_repo);
    static TensorSpec prod(const vespalib::string &expr, const ParamRepo &param_repo) {
        return EvalFixture(FastValueBuilderFactory::get(), expr, param_repo, true, false).result();
    }
};

} // namespace vespalib::eval::test
