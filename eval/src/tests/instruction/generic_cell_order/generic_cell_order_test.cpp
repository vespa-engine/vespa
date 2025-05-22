// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/instruction/generic_cell_order.h>
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

GenSpec G() { return GenSpec().seq(N()); }

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

TensorSpec perform_generic_cell_order(const TensorSpec &a, eval::CellOrder order, const ValueBuilderFactory &factory)
{
    Stash stash;
    auto lhs = value_from_spec(a, factory);
    auto res_type = lhs->type().map();
    auto my_op = GenericCellOrder::make_instruction(res_type, lhs->type(), order, stash);
    InterpretedFunction::EvalSingle single(factory, my_op);
    return spec_from_value(single.eval(std::vector<Value::CREF>({*lhs})));
}

void test_generic_cell_order_with(const ValueBuilderFactory &factory) {
    for (const auto &layout : layouts) {
        for (CellType in_type: CellTypeUtils::list_types()) {
            for (eval::CellOrder order: {eval::CellOrder::MAX, eval::CellOrder::MIN}) {
                auto lhs = layout.cpy().cells(in_type);
                if (lhs.bad_scalar()) continue;
                SCOPED_TRACE(fmt("order: %s\n===\nLHS: %s\n===\n",
                                 as_string(order).c_str(),
                                 lhs.gen().to_string().c_str()));
                auto expect = ReferenceOperations::cell_order(lhs, order);
                auto actual = perform_generic_cell_order(lhs, order, factory);
                EXPECT_EQ(actual, expect);
            }
        }
    }
}

TEST(GenericCellOrderTest, generic_cell_order_works_for_simple_values) {
    test_generic_cell_order_with(SimpleValueBuilderFactory::get());
}

TEST(GenericCellOrderTest, generic_cell_order_works_for_fast_values) {
    test_generic_cell_order_with(FastValueBuilderFactory::get());
}

GTEST_MAIN_RUN_ALL_TESTS()
