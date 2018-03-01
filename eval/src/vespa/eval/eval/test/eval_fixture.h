// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/vespalib/util/stash.h>
#include <set>

namespace vespalib::eval::test {

class EvalFixture
{
public:
    struct Param {
        TensorSpec value; // actual parameter value
        vespalib::string type;  // pre-defined type (could be abstract)
        bool is_mutable; // input will be mutable (if allow_mutable is true)
        Param(TensorSpec value_in, const vespalib::string &type_in, bool is_mutable_in)
            : value(std::move(value_in)), type(type_in), is_mutable(is_mutable_in) {}
        ~Param() {}
    };

    struct ParamRepo {
        std::map<vespalib::string,Param> map;
        ParamRepo() : map() {}
        ParamRepo &add(const vespalib::string &name, TensorSpec value_in, const vespalib::string &type_in, bool is_mutable_in) {
            map.insert_or_assign(name, Param(std::move(value_in), type_in, is_mutable_in));
            return *this;
        }
        ParamRepo &add(const vespalib::string &name, TensorSpec value, const vespalib::string &type) {
            return add(name, value, type, false);
        }
        ParamRepo &add_mutable(const vespalib::string &name, TensorSpec value, const vespalib::string &type) {
            return add(name, value, type, true);
        }
        ParamRepo &add(const vespalib::string &name, const TensorSpec &value) {
            return add(name, value, value.type(), false);
        }
        ParamRepo &add_mutable(const vespalib::string &name, const TensorSpec &value) {
            return add(name, value, value.type(), true);
        }
        ~ParamRepo() {}
    };

private:
    const TensorEngine           &_engine;
    Stash                         _stash;
    Function                      _function;
    NodeTypes                     _node_types;
    std::set<size_t>              _mutable_set;
    const TensorFunction         &_plain_tensor_function;
    const TensorFunction         &_patched_tensor_function;
    const TensorFunction         &_tensor_function;
    InterpretedFunction           _ifun;
    InterpretedFunction::Context  _ictx;
    std::vector<Value::UP>        _param_values;
    SimpleObjectParams            _params;
    TensorSpec                    _result;

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

public:
    EvalFixture(const TensorEngine &engine, const vespalib::string &expr, const ParamRepo &param_repo,
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
    static TensorSpec ref(const vespalib::string &expr, const ParamRepo &param_repo) {
        return EvalFixture(SimpleTensorEngine::ref(), expr, param_repo, false, false).result();
    }
    static TensorSpec prod(const vespalib::string &expr, const ParamRepo &param_repo) {
        return EvalFixture(tensor::DefaultTensorEngine::ref(), expr, param_repo, true, false).result();
    }
};

} // namespace vespalib::eval::test
