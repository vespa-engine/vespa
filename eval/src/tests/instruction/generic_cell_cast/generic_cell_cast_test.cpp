// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/instruction/generic_cell_cast.h>
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

TensorSpec perform_generic_cell_cast(const TensorSpec &a, CellType to, const ValueBuilderFactory &factory)
{
    Stash stash;
    auto lhs = value_from_spec(a, factory);
    auto res_type = lhs->type().cell_cast(to);
    auto my_op = GenericCellCast::make_instruction(res_type, lhs->type(), to, stash);
    InterpretedFunction::EvalSingle single(factory, my_op);
    return spec_from_value(single.eval(std::vector<Value::CREF>({*lhs})));
}

void test_generic_cell_cast_with(const ValueBuilderFactory &factory) {
    for (const auto &layout : layouts) {
        for (CellType in_type: CellTypeUtils::list_types()) {
            for (CellType out_type: CellTypeUtils::list_types()) {
                auto lhs = layout.cpy().cells(in_type);
                auto res_check = layout.cpy().cells(out_type);
                if (lhs.bad_scalar() || res_check.bad_scalar()) continue;
                SCOPED_TRACE(fmt("\n===\nLHS: %s\n===\n", lhs.gen().to_string().c_str()));
                auto expect = ReferenceOperations::cell_cast(lhs, out_type);
                auto actual = perform_generic_cell_cast(lhs, out_type, factory);
                EXPECT_EQ(actual, expect);
            }
        }
    }
}

TEST(GenericCellCastTest, generic_cell_cast_works_for_simple_values) {
    test_generic_cell_cast_with(SimpleValueBuilderFactory::get());
}

TEST(GenericCellCastTest, generic_cell_cast_works_for_fast_values) {
    test_generic_cell_cast_with(FastValueBuilderFactory::get());
}

GTEST_MAIN_RUN_ALL_TESTS()
