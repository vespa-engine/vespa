// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

std::vector<std::pair<vespalib::string,vespalib::string>> add_layouts = {
    {     "x4_1",     "x4_2" },
    { "x4_2y4_1", "x4_1y4_2" },
    {   "x3y4_1",   "x3y4_2" }
};

TensorSpec reference_add(const TensorSpec &a, const TensorSpec &b) {
    TensorSpec result(a.type());
    for (const auto &cell: b.cells()) {
        result.add(cell.first, cell.second);
    }
    auto end_iter = b.cells().end();
    for (const auto &cell: a.cells()) {
        auto iter = b.cells().find(cell.first);
        if (iter == end_iter) {
            result.add(cell.first, cell.second);
        }
    }
    return result.normalize();
}

Value::UP try_partial_add(const TensorSpec &a, const TensorSpec &b) {
    const auto &factory = SimpleValueBuilderFactory::get();
    auto lhs = value_from_spec(a, factory);
    auto rhs = value_from_spec(b, factory);
    return TensorPartialUpdate::add(*lhs, *rhs, factory);
}

TensorSpec perform_partial_add(const TensorSpec &a, const TensorSpec &b) {
    auto up = try_partial_add(a, b);
    EXPECT_TRUE(up);
    return spec_from_value(*up);
}

TEST(PartialAddTest, partial_add_works_for_simple_values) {
    for (const auto &layouts: add_layouts) {
        for (auto lhs_ct: CellTypeUtils::list_types()) {
            for (auto rhs_ct: CellTypeUtils::list_types()) {
                TensorSpec lhs = GenSpec::from_desc(layouts.first).cells(lhs_ct).seq(N());
                TensorSpec rhs = GenSpec::from_desc(layouts.second).cells(rhs_ct).seq(Div16(N()));
                SCOPED_TRACE(fmt("\n===\nLHS: %s\nRHS: %s\n===\n", lhs.to_string().c_str(), rhs.to_string().c_str()));
                auto expect = reference_add(lhs, rhs);
                auto actual = perform_partial_add(lhs, rhs);
                EXPECT_EQ(actual, expect);
            }
        }
    }
}

std::vector<std::pair<vespalib::string,vespalib::string>> bad_layouts = {
    {       "x3",     "x3y1" },
    {     "x3y1",       "x3" },
    {     "x3y3",   "x3y3_1" },
    {   "x3y3_1",     "x3y3" },
    {     "x3_1", "x3_1y3_1" },
    { "x3_1y3_1",     "x3_1" },
    {     "x3_1",   "x3_1y1" }
};

TEST(PartialAddTest, partial_add_returns_nullptr_on_invalid_inputs) {
    for (const auto &layouts: bad_layouts) {
        TensorSpec lhs = GenSpec::from_desc(layouts.first).seq(N());
        TensorSpec rhs = GenSpec::from_desc(layouts.second).seq(Div16(N()));
        SCOPED_TRACE(fmt("\n===\nLHS: %s\nRHS: %s\n===\n", lhs.to_string().c_str(), rhs.to_string().c_str()));
        auto actual = try_partial_add(lhs, rhs);
        auto expect = Value::UP();
        EXPECT_EQ(actual, expect);
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
