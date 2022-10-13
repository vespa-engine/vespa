// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/vespalib/util/stash.h>
#include <set>
#include <functional>
#include "gen_spec.h"
#include "cell_type_space.h"
#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/util/unwind_message.h>

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

        ParamRepo &add(const vespalib::string &name, TensorSpec value);
        ParamRepo &add_mutable(const vespalib::string &name, TensorSpec spec);

        // produce 4 variants: float/double * mutable/const
        ParamRepo &add_variants(const vespalib::string &name_base, const GenSpec &spec);

        // add a parameter that is generated based on a description.
        //
        // the description may start with '@' to indicate that the
        // parameter is mutable. The rest of the description must be a
        // valid parameter to the GenSpec::from_desc() function.
        ParamRepo &add(const vespalib::string &name, const vespalib::string &desc,
                       CellType cell_type, GenSpec::seq_t seq);

        // add a parameter that is generated based on a description,
        // where the description is also the name of the parameter.
        //
        // This is a convenience wrapper for the function adding a
        // parameter based on a descriprion. The only thing this
        // function does is use the description as parameter name and
        // strip an optional suffix starting with '$' from the name
        // before using it as a descriprion. (to support multiple
        // parameters with the same description).
        ParamRepo &add(const vespalib::string &name_desc, CellType cell_type, GenSpec::seq_t seq);

        ~ParamRepo() {}
    };

private:
    const ValueBuilderFactory      &_factory;
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
    const Value                    &_result_value;
    TensorSpec                      _result;

    template <typename T>
    static void find_all(const TensorFunction &node, std::vector<const T *> &list) {
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

    template <typename FunInfo>
    auto verify_callback(const FunInfo &verificator,
                         const typename FunInfo::LookFor &what) const
        -> decltype(verificator.verify(what))
    {
        return verificator.verify(what);
    }
    template <typename FunInfo>
    auto verify_callback(const FunInfo &verificator,
                         const typename FunInfo::LookFor &what) const
        -> decltype(verificator.verify(*this, what))
    {
        return verificator.verify(*this, what);
    }

public:
    EvalFixture(const ValueBuilderFactory &factory, const vespalib::string &expr, const ParamRepo &param_repo,
                bool optimized = true, bool allow_mutable = false);
    ~EvalFixture() {}
    template <typename T>
    std::vector<const T *> find_all() const {
        std::vector<const T *> list;
        find_all(_tensor_function, list);
        return list;
    }
    const Value &result_value() const { return _result_value; }
    const Value &param_value(size_t idx) const { return *(_param_values[idx]); }
    const TensorSpec &result() const { return _result; }
    size_t num_params() const;
    static TensorSpec ref(const vespalib::string &expr, const ParamRepo &param_repo);
    static TensorSpec prod(const vespalib::string &expr, const ParamRepo &param_repo) {
        return EvalFixture(FastValueBuilderFactory::get(), expr, param_repo, true, false).result();
    }

    static const ValueBuilderFactory &prod_factory() { return FastValueBuilderFactory::get(); }
    static const ValueBuilderFactory &test_factory() { return SimpleValueBuilderFactory::get(); }

    // Verify the evaluation result and specific tensor function
    // details for the given expression with the given parameters. A
    // parameter can be tagged as mutable by giving it a name starting
    // with '@'. Parameters must be given in automatic discovery order.

    template <typename FunInfo>
    static void verify(const vespalib::string &expr, const std::vector<FunInfo> &fun_info, std::vector<GenSpec> param_specs) {
        UNWIND_MSG("in verify(%s) with %zu FunInfo", expr.c_str(), fun_info.size());
        auto fun = Function::parse(expr);
        REQUIRE_EQ(fun->num_params(), param_specs.size());
        EvalFixture::ParamRepo param_repo;
        for (size_t i = 0; i < fun->num_params(); ++i) {
            if (fun->param_name(i)[0] == '@') {
                param_repo.add_mutable(fun->param_name(i), param_specs[i]);
            } else {
                param_repo.add(fun->param_name(i), param_specs[i]);
            }
        }
        EvalFixture fixture(prod_factory(), expr, param_repo, true, true);
        EvalFixture slow_fixture(prod_factory(), expr, param_repo, false, false);
        EvalFixture test_fixture(test_factory(), expr, param_repo, true, true);
        REQUIRE_EQ(fixture.result(), test_fixture.result());
        REQUIRE_EQ(fixture.result(), slow_fixture.result());
        REQUIRE_EQ(fixture.result(), EvalFixture::ref(expr, param_repo));
        auto info = fixture.find_all<typename FunInfo::LookFor>();
        REQUIRE_EQ(info.size(), fun_info.size());
        for (size_t i = 0; i < fun_info.size(); ++i) {
            fixture.verify_callback<FunInfo>(fun_info[i], *info[i]);
        }
    }

    // Verify the evaluation result and specific tensor function
    // details for the given expression with different combinations of
    // cell types. Parameter names must be valid GenSpec descriptions
    // ('a5b8'), with an optional mutable prefix ('@a5b8') to denote
    // parameters that may be modified, and an optional non-descriptive
    // trailer starting with '$' ('a5b3$2') to allow multiple
    // parameters with the same description as well as scalars
    // ('$this_is_a_scalar').

    template <typename FunInfo>
    static void verify(const vespalib::string &expr, const std::vector<FunInfo> &fun_info, CellTypeSpace cell_type_space) {
        UNWIND_MSG("in verify(%s) with %zu FunInfo", expr.c_str(), fun_info.size());
        auto fun = Function::parse(expr);
        REQUIRE_EQ(fun->num_params(), cell_type_space.n());
        for (; cell_type_space.valid(); cell_type_space.next()) {
            auto cell_types = cell_type_space.get();
            EvalFixture::ParamRepo param_repo;
            for (size_t i = 0; i < fun->num_params(); ++i) {
                param_repo.add(fun->param_name(i), cell_types[i], N(1 + i));
            }
            EvalFixture fixture(prod_factory(), expr, param_repo, true, true);
            EvalFixture slow_fixture(prod_factory(), expr, param_repo, false, false);
            EvalFixture test_fixture(test_factory(), expr, param_repo, true, true);
            REQUIRE_EQ(fixture.result(), test_fixture.result());
            REQUIRE_EQ(fixture.result(), slow_fixture.result());
            REQUIRE_EQ(fixture.result(), EvalFixture::ref(expr, param_repo));
            auto info = fixture.find_all<typename FunInfo::LookFor>();
            REQUIRE_EQ(info.size(), fun_info.size());
            for (size_t i = 0; i < fun_info.size(); ++i) {
                fixture.verify_callback<FunInfo>(fun_info[i], *info[i]);
            }
        }
    }
};

} // namespace vespalib::eval::test
