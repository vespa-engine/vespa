// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/streamed/streamed_value_builder_factory.h>
#include <vespa/eval/eval/test/reference_operations.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/instruction/generic_join.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/test/tensor_model.hpp>
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

std::vector<Layout> layouts = {
    {},
    {x(3)},
    {x(3),y(5)},
    {x(3),y(5),z(7)},
    float_cells({x(3),y(5),z(7)}),
    {x({"a","b","c"})},
    {x({"a","b","c"}),y({"foo","bar"})},
    {x({"a","b","c"}),y({"foo","bar"}),z({"i","j","k","l"})},
    float_cells({x({"a","b","c"}),y({"foo","bar"}),z({"i","j","k","l"})}),
    {x(3),y({"foo", "bar"}),z(7)},
    {x({"a","b","c"}),y(5),z({"i","j","k","l"})},
    float_cells({x({"a","b","c"}),y(5),z({"i","j","k","l"})})
};

std::vector<Layout> join_layouts = {
    {},                                                 {},
    {x(5)},                                             {x(5)},
    {x(5)},                                             {y(5)},
    {x(5)},                                             {x(5),y(5)},
    {y(3)},                                             {x(2),z(3)},
    {x(3),y(5)},                                        {y(5),z(7)},
    float_cells({x(3),y(5)}),                           {y(5),z(7)},
    {x(3),y(5)},                                        float_cells({y(5),z(7)}),
    float_cells({x(3),y(5)}),                           float_cells({y(5),z(7)}),
    {x({"a","b","c"})},                                 {x({"a","b","c"})},
    {x({"a","b","c"})},                                 {x({"a","b"})},
    {x({"a","b","c"})},                                 {y({"foo","bar","baz"})},
    {x({"a","b","c"})},                                 {x({"a","b","c"}),y({"foo","bar","baz"})},
    {x({"a","b"}),y({"foo","bar","baz"})},              {x({"a","b","c"}),y({"foo","bar"})},
    {x({"a","b"}),y({"foo","bar","baz"})},              {y({"foo","bar"}),z({"i","j","k","l"})},
    float_cells({x({"a","b"}),y({"foo","bar","baz"})}), {y({"foo","bar"}),z({"i","j","k","l"})},
    {x({"a","b"}),y({"foo","bar","baz"})},              float_cells({y({"foo","bar"}),z({"i","j","k","l"})}),
    float_cells({x({"a","b"}),y({"foo","bar","baz"})}), float_cells({y({"foo","bar"}),z({"i","j","k","l"})}),
    {x(3),y({"foo", "bar"})},                           {y({"foo", "bar"}),z(7)},
    {x({"a","b","c"}),y(5)},                            {y(5),z({"i","j","k","l"})},
    float_cells({x({"a","b","c"}),y(5)}),               {y(5),z({"i","j","k","l"})},
    {x({"a","b","c"}),y(5)},                            float_cells({y(5),z({"i","j","k","l"})}),
    float_cells({x({"a","b","c"}),y(5)}),               float_cells({y(5),z({"i","j","k","l"})})
};

TensorSpec streamed_value_join(const TensorSpec &a, const TensorSpec &b, join_fun_t function) {
    Stash stash;
    const auto &factory = StreamedValueBuilderFactory::get();
    auto lhs = value_from_spec(a, factory);
    auto rhs = value_from_spec(b, factory);
    auto my_op = GenericJoin::make_instruction(lhs->type(), rhs->type(), function, factory, stash);
    InterpretedFunction::EvalSingle single(factory, my_op);
    return spec_from_value(single.eval(std::vector<Value::CREF>({*lhs,*rhs})));
}

TEST(StreamedValueTest, streamed_values_can_be_converted_from_and_to_tensor_spec) {
    for (const auto &layout: layouts) {
        TensorSpec expect = spec(layout, N());
        std::unique_ptr<Value> value = value_from_spec(expect, StreamedValueBuilderFactory::get());
        TensorSpec actual = spec_from_value(*value);
        EXPECT_EQ(actual, expect);
    }
}

TEST(StreamedValueTest, streamed_values_can_be_copied) {
    for (const auto &layout: layouts) {
        TensorSpec expect = spec(layout, N());
        std::unique_ptr<Value> value = value_from_spec(expect, StreamedValueBuilderFactory::get());
        std::unique_ptr<Value> copy = StreamedValueBuilderFactory::get().copy(*value);
        TensorSpec actual = spec_from_value(*copy);
        EXPECT_EQ(actual, expect);
    }
}

TEST(StreamedValueTest, streamed_value_can_be_built_and_inspected) {
    ValueType type = ValueType::from_spec("tensor<float>(x{},y[2],z{})");
    const auto &factory = StreamedValueBuilderFactory::get();
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
    auto view = value->index().create_view({0});
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

TEST(StreamedValueTest, new_generic_join_works_for_streamed_values) {
    ASSERT_TRUE((join_layouts.size() % 2) == 0);
    for (size_t i = 0; i < join_layouts.size(); i += 2) {
        TensorSpec lhs = spec(join_layouts[i], Div16(N()));
        TensorSpec rhs = spec(join_layouts[i + 1], Div16(N()));
        for (auto fun: {operation::Add::f, operation::Sub::f, operation::Mul::f, operation::Max::f}) {
            SCOPED_TRACE(fmt("\n===\nLHS: %s\nRHS: %s\n===\n", lhs.to_string().c_str(), rhs.to_string().c_str()));
            auto expect = ReferenceOperations::join(lhs, rhs, fun);
            auto actual = streamed_value_join(lhs, rhs, fun);
            EXPECT_EQ(actual, expect);
        }
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
