// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/instruction/generic_join.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/test/reference_operations.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::instruction;
using namespace vespalib::eval::test;

using vespalib::make_string_short::fmt;

using PA = std::vector<string_id *>;
using CPA = std::vector<const string_id *>;

using Handle = SharedStringRepo::Handle;

vespalib::string as_str(string_id label) { return Handle::string_from_id(label); }

GenSpec G() { return GenSpec(); }

const std::vector<GenSpec> layouts = {
    G(),
    G().idx("x", 3),
    G().idx("x", 3).idx("y", 5),
    G().idx("x", 3).idx("y", 5).idx("z", 7),
    G().map("x", {"a","b","c"}),
    G().map("x", {"a","b","c"}).map("y", {"foo","bar"}),
    G().map("x", {"a","b","c"}).map("y", {"foo","bar"}).map("z", {"i","j","k","l"}),
    G().idx("x", 3).map("y", {"foo", "bar"}).idx("z", 7),
    G().map("x", {"a","b","c"}).idx("y", 5).map("z", {"i","j","k","l"})
};

const std::vector<GenSpec> join_layouts = {
    G(),                                                      G(),
    G().idx("x", 5),                                          G().idx("x", 5),
    G().idx("x", 5),                                          G().idx("y", 5),
    G().idx("x", 5),                                          G().idx("x", 5).idx("y", 5),
    G().idx("y", 3),                                          G().idx("x", 2).idx("z", 3),
    G().idx("x", 3).idx("y", 5),                              G().idx("y", 5).idx("z", 7),
    G().map("x", {"a","b","c"}),                              G().map("x", {"a","b","c"}),
    G().map("x", {"a","b","c"}),                              G().map("x", {"a","b"}),
    G().map("x", {"a","b","c"}),                              G().map("y", {"foo","bar","baz"}),
    G().map("x", {"a","b","c"}),                              G().map("x", {"a","b","c"}).map("y", {"foo","bar","baz"}),
    G().map("x", {"a","b"}).map("y", {"foo","bar","baz"}),    G().map("x", {"a","b","c"}).map("y", {"foo","bar"}),
    G().map("x", {"a","b"}).map("y", {"foo","bar","baz"}),    G().map("y", {"foo","bar"}).map("z", {"i","j","k","l"}),
    G().idx("x", 3).map("y", {"foo", "bar"}),                 G().map("y", {"foo","bar"}).idx("z", 7),
    G().map("x", {"a","b","c"}).idx("y", 5),                  G().idx("y", 5).map("z", {"i","j","k","l"})

};

TensorSpec simple_value_join(const TensorSpec &a, const TensorSpec &b, join_fun_t function) {
    Stash stash;
    const auto &factory = SimpleValueBuilderFactory::get();
    auto lhs = value_from_spec(a, factory);
    auto rhs = value_from_spec(b, factory);
    auto res_type = ValueType::join(lhs->type(), rhs->type());
    auto my_op = GenericJoin::make_instruction(res_type, lhs->type(), rhs->type(), function, factory, stash);
    InterpretedFunction::EvalSingle single(factory, my_op);
    return spec_from_value(single.eval(std::vector<Value::CREF>({*lhs,*rhs})));
}

TEST(SimpleValueTest, simple_values_can_be_converted_from_and_to_tensor_spec) {
    for (const auto &layout: layouts) {
        for (CellType ct : CellTypeUtils::list_types()) {
            auto expect = layout.cpy().cells(ct);
            if (expect.bad_scalar()) continue;
            std::unique_ptr<Value> value = value_from_spec(expect, SimpleValueBuilderFactory::get());
            TensorSpec actual = spec_from_value(*value);
            EXPECT_EQ(actual, expect);
        }
    }
}

TEST(SimpleValueTest, simple_values_can_be_copied) {
    for (const auto &layout: layouts) {
        for (CellType ct : CellTypeUtils::list_types()) {
            auto expect = layout.cpy().cells(ct);
            if (expect.bad_scalar()) continue;
            std::unique_ptr<Value> value = value_from_spec(expect, SimpleValueBuilderFactory::get());
            std::unique_ptr<Value> copy = SimpleValueBuilderFactory::get().copy(*value);
            TensorSpec actual = spec_from_value(*copy);
            EXPECT_EQ(actual, expect);
        }
    }
}

TEST(SimpleValueTest, simple_value_can_be_built_and_inspected) {
    ValueType type = ValueType::from_spec("tensor<float>(x{},y[2],z{})");
    const auto &factory = SimpleValueBuilderFactory::get();
    std::unique_ptr<ValueBuilder<float>> builder = factory.create_value_builder<float>(type);
    float seq = 0.0;
    for (vespalib::string x: {"a", "b", "c"}) {
        for (vespalib::string y: {"aa", "bb"}) {
            std::vector<vespalib::stringref> addr = {x, y};
            auto subspace = builder->add_subspace(addr);
            EXPECT_EQ(subspace.size(), 2);
            subspace[0] = seq + 1.0;
            subspace[1] = seq + 5.0;
            seq += 10.0;
        }
        seq += 100.0;
    }
    std::unique_ptr<Value> value = builder->build(std::move(builder));
    EXPECT_EQ(value->index().size(), 6);
    std::vector<size_t> view_dims = { 0 };
    auto view = value->index().create_view(view_dims);
    Handle query_handle("b");
    string_id query = query_handle.id();
    string_id label;
    size_t subspace;
    std::map<vespalib::string,size_t> result;
    view->lookup(CPA{&query});
    while (view->next_result(PA{&label}, subspace)) {
        result[as_str(label)] = subspace;
    }
    EXPECT_EQ(result.size(), 2);
    EXPECT_EQ(result["aa"], 2);
    EXPECT_EQ(result["bb"], 3);
}

GenSpec::seq_t N_16ths = [] (size_t i) noexcept { return (i + 1.0) / 16.0; };

TEST(SimpleValueTest, new_generic_join_works_for_simple_values) {
    ASSERT_TRUE((join_layouts.size() % 2) == 0);
    for (size_t i = 0; i < join_layouts.size(); i += 2) {
        const auto l = join_layouts[i].cpy().seq(N_16ths);
        const auto r = join_layouts[i + 1].cpy().seq(N_16ths);
        for (CellType lct : CellTypeUtils::list_types()) {
            auto lhs = l.cpy().cells(lct);
            if (lhs.bad_scalar()) continue;
            for (CellType rct : CellTypeUtils::list_types()) {
                auto rhs = r.cpy().cells(rct);
                if (rhs.bad_scalar()) continue;
                for (auto fun: {operation::Add::f, operation::Sub::f, operation::Mul::f, operation::Div::f}) {
                    SCOPED_TRACE(fmt("\n===\nLHS: %s\nRHS: %s\n===\n", lhs.gen().to_string().c_str(), rhs.gen().to_string().c_str()));
                    auto expect = ReferenceOperations::join(lhs, rhs, fun);
                    auto actual = simple_value_join(lhs, rhs, fun);
                    EXPECT_EQ(actual, expect);
                }
            }
        }
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
