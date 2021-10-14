// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/document/update/tensor_partial_update.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <optional>

using namespace document;
using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;

using vespalib::make_string_short::fmt;

std::vector<std::pair<vespalib::string,vespalib::string>> modify_layouts = {
    {       "x4_1",         "x4_1" },
    {       "x4_1",         "x4_2" },
    {         "x4",         "x4_2" },
    {   "x4_1y4_2",     "x4_2y4_1" },
    {   "x4y4_1z4", "x4_2y4_2z4_2" },
    {       "x3y2",     "x2_1y2_1" }
};

TensorSpec::Address sparsify(const TensorSpec::Address &input) {
    TensorSpec::Address output;
    for (const auto & kv : input) {
        if (kv.second.is_indexed()) {
            auto val = fmt("%zu", kv.second.index);
            output.emplace(kv.first, val);
        } else {
            output.emplace(kv.first, kv.second);
        }
    }
    return output;
}

TensorSpec reference_modify(const TensorSpec &a, const TensorSpec &b, join_fun_t fun) {
    TensorSpec result(a.type());
    auto end_iter = b.cells().end();
    for (const auto &cell: a.cells()) {
        double v = cell.second;
        auto sparse_addr = sparsify(cell.first);
        auto iter = b.cells().find(sparse_addr);
        if (iter == end_iter) {
            result.add(cell.first, v);
        } else {
            result.add(cell.first, fun(v, iter->second));
        }
    }
    return result.normalize();
}

Value::UP try_partial_modify(const TensorSpec &a, const TensorSpec &b, join_fun_t fun) {
    const auto &factory = SimpleValueBuilderFactory::get();
    auto lhs = value_from_spec(a, factory);
    auto rhs = value_from_spec(b, factory);
    return TensorPartialUpdate::modify(*lhs, fun, *rhs, factory);
}

TensorSpec perform_partial_modify(const TensorSpec &a, const TensorSpec &b, join_fun_t fun) {
    auto up = try_partial_modify(a, b, fun);
    EXPECT_TRUE(up);
    return spec_from_value(*up);
}

TEST(PartialModifyTest, partial_modify_works_for_simple_values) {
    for (const auto &layouts: modify_layouts) {
        for (auto lhs_ct: CellTypeUtils::list_types()) {
            for (auto rhs_ct: CellTypeUtils::list_types()) {
                TensorSpec lhs = GenSpec::from_desc(layouts.first).cells(lhs_ct).seq(N());
                TensorSpec rhs = GenSpec::from_desc(layouts.second).cells(rhs_ct).seq(Div16(N()));
                SCOPED_TRACE(fmt("\n===\nLHS: %s\nRHS: %s\n===\n", lhs.to_string().c_str(), rhs.to_string().c_str()));
                for (auto fun: {operation::Add::f, operation::Mul::f, operation::Sub::f}) {
                    auto expect = reference_modify(lhs, rhs, fun);
                    auto actual = perform_partial_modify(lhs, rhs, fun);
                    EXPECT_EQ(actual, expect);
                }
                auto fun = [](double, double keep) { return keep; };
                auto expect = reference_modify(lhs, rhs, fun);
                auto actual = perform_partial_modify(lhs, rhs, fun);
                EXPECT_EQ(actual, expect);
            }
        }
    }
}

std::vector<std::pair<vespalib::string,vespalib::string>> bad_layouts = {
    {       "x3",       "x3" },
    {   "x3y4_1",   "x3y4_1" },
    {     "x4_1", "x4_1y4_1" },
    { "x4_1y4_1",     "x4_1" },
    {     "x4_1",   "x4_1y1" }
};

TEST(PartialModifyTest, partial_modify_returns_nullptr_on_invalid_inputs) {
    for (const auto &layouts: bad_layouts) {
        TensorSpec lhs = GenSpec::from_desc(layouts.first).seq(N());
        TensorSpec rhs = GenSpec::from_desc(layouts.second).seq(Div16(N()));
        SCOPED_TRACE(fmt("\n===\nLHS: %s\nRHS: %s\n===\n", lhs.to_string().c_str(), rhs.to_string().c_str()));
        for (auto fun: {operation::Add::f}) {
            auto actual = try_partial_modify(lhs, rhs, fun);
            auto expect = Value::UP();
            EXPECT_EQ(actual, expect);
        }
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
