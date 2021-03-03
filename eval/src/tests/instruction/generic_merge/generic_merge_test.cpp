// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/instruction/generic_merge.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/test/reference_operations.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <optional>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::instruction;
using namespace vespalib::eval::test;

using vespalib::make_string_short::fmt;

GenSpec G() { return GenSpec(); }

GenSpec::seq_t N_16ths = [] (size_t i) noexcept { return (i + 1.0) / 16.0; };

const std::vector<GenSpec> merge_layouts = {
    G(),                                                      G(),
    G().idx("x", 5),                                          G().idx("x", 5),
    G().idx("x", 3).idx("y", 5),                              G().idx("x", 3).idx("y", 5),
    G().map("x", {"a","b","c"}),                              G().map("x", {"a","b","c"}),
    G().map("x", {"a","b","c"}),                              G().map("x", {"c","d","e"}),
    G().map("x", {"a","c","e"}),                              G().map("x", {"b","c","d"}),
    G().map("x", {"b","c","d"}),                              G().map("x", {"a","c","e"}),
    G().map("x", {"a","b","c"}),                              G().map("x", {"c","d"}),
    G().map("x", {"a","b"}).map("y", {"foo","bar","baz"}),    G().map("x", {"b","c"}).map("y", {"any","foo","bar"}),
    G().idx("x", 3).map("y", {"foo", "bar"}),                 G().idx("x", 3).map("y", {"baz", "bar"}),
    G().map("x", {"a","b","c"}).idx("y", 5),                  G().map("x", {"b","c","d"}).idx("y", 5)
};

TensorSpec perform_generic_merge(const TensorSpec &a, const TensorSpec &b, join_fun_t fun, const ValueBuilderFactory &factory) {
    Stash stash;
    auto lhs = value_from_spec(a, factory);
    auto rhs = value_from_spec(b, factory);
    auto res_type = ValueType::merge(lhs->type(), rhs->type());
    auto my_op = GenericMerge::make_instruction(res_type, lhs->type(), rhs->type(), fun, factory, stash);
    InterpretedFunction::EvalSingle single(factory, my_op);
    return spec_from_value(single.eval(std::vector<Value::CREF>({*lhs, *rhs})));
}

void test_generic_merge_with(const ValueBuilderFactory &factory) {
    ASSERT_TRUE((merge_layouts.size() % 2) == 0);
    for (size_t i = 0; i < merge_layouts.size(); i += 2) {
        const auto l = merge_layouts[i];
        const auto r = merge_layouts[i+1].cpy().seq(N_16ths);
        for (CellType lct : CellTypeUtils::list_types()) {
            auto lhs = l.cpy().cells(lct);
            if (lhs.bad_scalar()) continue;
            for (CellType rct : CellTypeUtils::list_types()) {
                auto rhs = r.cpy().cells(rct);
                if (rhs.bad_scalar()) continue;
                SCOPED_TRACE(fmt("\n===\nLHS: %s\nRHS: %s\n===\n", lhs.gen().to_string().c_str(), rhs.gen().to_string().c_str()));
                for (auto fun: {operation::Add::f, operation::Mul::f, operation::Sub::f, operation::Max::f}) {
                    auto expect = ReferenceOperations::merge(lhs, rhs, fun);
                    auto actual = perform_generic_merge(lhs, rhs, fun, factory);
                    EXPECT_EQ(actual, expect);
                }
            }
        }
    }
}

TEST(GenericMergeTest, generic_merge_works_for_simple_values) {
    test_generic_merge_with(SimpleValueBuilderFactory::get());
}

TEST(GenericMergeTest, generic_merge_works_for_fast_values) {
    test_generic_merge_with(FastValueBuilderFactory::get());
}


GTEST_MAIN_RUN_ALL_TESTS()
