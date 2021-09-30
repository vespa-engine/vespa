// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/instruction/generic_map.h>
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

GenSpec::seq_t N_16ths = [] (size_t i) noexcept { return (i + 1.0) / 16.0; };

GenSpec G() { return GenSpec().seq(N_16ths); }

const std::vector<GenSpec> map_layouts = {
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

TensorSpec perform_generic_map(const TensorSpec &a, map_fun_t func, const ValueBuilderFactory &factory)
{
    Stash stash;
    auto lhs = value_from_spec(a, factory);
    auto res_type = lhs->type().map();
    auto my_op = GenericMap::make_instruction(res_type, lhs->type(), func, stash);
    InterpretedFunction::EvalSingle single(factory, my_op);
    const auto & v = single.eval(std::vector<Value::CREF>({*lhs}));
    return spec_from_value(v);
}

void test_generic_map_with(const ValueBuilderFactory &factory) {
    for (const auto &layout : map_layouts) {
        for (CellType ct : CellTypeUtils::list_types()) {
            auto lhs = layout.cpy().cells(ct);
            if (lhs.bad_scalar()) continue;
            for (auto func : {operation::Floor::f, operation::Fabs::f, operation::Square::f, operation::Inv::f}) {
                SCOPED_TRACE(fmt("\n===\nLHS: %s\n===\n", lhs.gen().to_string().c_str()));
                auto expect = ReferenceOperations::map(lhs, func);
                auto actual = perform_generic_map(lhs, func, factory);
                EXPECT_EQ(actual, expect);
            }
        }
    }
}

TEST(GenericMapTest, generic_map_works_for_simple_values) {
    test_generic_map_with(SimpleValueBuilderFactory::get());
}

TEST(GenericMapTest, generic_map_works_for_fast_values) {
    test_generic_map_with(FastValueBuilderFactory::get());
}

GTEST_MAIN_RUN_ALL_TESTS()
