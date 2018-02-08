// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/vespalib/util/stash.h>

namespace vespalib::eval::test {

class EvalFixture
{
public:
    struct Param {
        TensorSpec value; // actual parameter value
        vespalib::string type;  // pre-defined type (could be abstract)
        Param(TensorSpec value_in)
            : value(std::move(value_in)), type(value.type()) {}
        Param(TensorSpec value_in, const vespalib::string &type_in)
            : value(std::move(value_in)), type(type_in) {}
        ~Param() {}
    };

    struct ParamRepo {
        std::map<vespalib::string,Param> map;
        ParamRepo() : map() {}
        ParamRepo &add(const vespalib::string &name, TensorSpec value_in) {
            map.insert_or_assign(name, Param(std::move(value_in)));
            return *this;
        }
        ParamRepo &add(const vespalib::string &name, TensorSpec value_in, const vespalib::string &type_in) {
            map.insert_or_assign(name, Param(std::move(value_in), type_in));
            return *this;
        }
        ~ParamRepo() {}
    };

private:
    const TensorEngine           &_engine;
    Stash                         _stash;
    Function                      _function;
    NodeTypes                     _node_types;
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
    EvalFixture(const TensorEngine &engine, const vespalib::string &expr, const ParamRepo &param_repo, bool optimized);
    ~EvalFixture() {}
    template <typename T>
    std::vector<const T *> find_all() {
        std::vector<const T *> list;
        find_all(_tensor_function, list);
        return list;
    }
    const TensorSpec &result() const { return _result; }
    static TensorSpec ref(const vespalib::string &expr, const ParamRepo &param_repo) {
        return EvalFixture(SimpleTensorEngine::ref(), expr, param_repo, false).result();
    }
    static TensorSpec prod(const vespalib::string &expr, const ParamRepo &param_repo) {
        return EvalFixture(tensor::DefaultTensorEngine::ref(), expr, param_repo, true).result();
    }
};

} // namespace vespalib::eval::test
