// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/instruction/universal_dot_product.h>
#include <vespa/eval/eval/test/reference_operations.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;

using vespalib::make_string_short::fmt;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();

GenSpec::seq_t N_16ths = [] (size_t i) noexcept { return (i + 33.0) / 16.0; };

GenSpec G() { return GenSpec().seq(N_16ths); }

const std::vector<GenSpec> layouts = {
    G(),                                                         G(),
    G().idx("x", 5),                                             G().idx("x", 5),
    G().idx("x", 5),                                             G().idx("y", 5),
    G().idx("x", 5),                                             G().idx("x", 5).idx("y", 5),
    G().idx("y", 3),                                             G().idx("x", 2).idx("z", 3),
    G().idx("x", 3).idx("y", 5),                                 G().idx("y", 5).idx("z", 7),
    G().map("x", {"a","b","c"}),                                 G().map("x", {"a","b","c"}),
    G().map("x", {"a","b","c"}),                                 G().map("x", {"a","b"}),
    G().map("x", {"a","b","c"}),                                 G().map("y", {"foo","bar","baz"}),
    G().map("x", {"a","b","c"}),                                 G().map("x", {"a","b","c"}).map("y", {"foo","bar","baz"}),
    G().map("x", {"a","b"}).map("y", {"foo","bar","baz"}),       G().map("x", {"a","b","c"}).map("y", {"foo","bar"}),
    G().map("x", {"a","b"}).map("y", {"foo","bar","baz"}),       G().map("y", {"foo","bar"}).map("z", {"i","j","k","l"}),
    G().idx("x", 3).map("y", {"foo", "bar"}),                    G().map("y", {"foo", "bar"}).idx("z", 7),
    G().map("x", {"a","b","c"}).idx("y", 5),                     G().idx("y", 5).map("z", {"i","j","k","l"})
};

const std::vector<std::vector<vespalib::string>> reductions = {
    {}, {"x"}, {"y"}, {"z"}, {"x", "y"}, {"x", "z"}, {"y", "z"}
};

TensorSpec perform_dot_product(const TensorSpec &a, const TensorSpec &b, const std::vector<vespalib::string> &dims)
{
    Stash stash;
    auto lhs = value_from_spec(a, prod_factory);
    auto rhs = value_from_spec(b, prod_factory);
    auto res_type = ValueType::join(lhs->type(), rhs->type()).reduce(dims);
    EXPECT_FALSE(res_type.is_error());
    UniversalDotProduct dot_product(res_type,
                                    tensor_function::inject(lhs->type(), 0, stash),
                                    tensor_function::inject(rhs->type(), 1, stash));
    auto my_op = dot_product.compile_self(prod_factory, stash);
    InterpretedFunction::EvalSingle single(prod_factory, my_op);
    return spec_from_value(single.eval(std::vector<Value::CREF>({*lhs,*rhs})));
}

TEST(UniversalDotProductTest, generic_dot_product_works_for_various_cases) {
    size_t test_cases = 0;
    ASSERT_TRUE((layouts.size() % 2) == 0);
    for (size_t i = 0; i < layouts.size(); i += 2) {
        const auto &l = layouts[i];
        const auto &r = layouts[i+1];
        for (CellType lct : CellTypeUtils::list_types()) {
            auto lhs = l.cpy().cells(lct);
            if (lhs.bad_scalar()) continue;
            for (CellType rct : CellTypeUtils::list_types()) {
                auto rhs = r.cpy().cells(rct);
                if (rhs.bad_scalar()) continue;
                for (const std::vector<vespalib::string> &dims: reductions) {
                    if (ValueType::join(lhs.type(), rhs.type()).reduce(dims).is_error()) continue;
                    ++test_cases;
                    SCOPED_TRACE(fmt("\n===\nLHS: %s\nRHS: %s\n===\n", lhs.gen().to_string().c_str(), rhs.gen().to_string().c_str()));
                    auto expect = ReferenceOperations::reduce(ReferenceOperations::join(lhs, rhs, operation::Mul::f), Aggr::SUM, dims);
                    auto actual = perform_dot_product(lhs, rhs, dims);
                    // fprintf(stderr, "\n===\nLHS: %s\nRHS: %s\n===\nRESULT: %s\n===\n", lhs.gen().to_string().c_str(), rhs.gen().to_string().c_str(), actual.to_string().c_str());
                    EXPECT_EQ(actual, expect);
                }
            }
        }
    }
    EXPECT_GT(test_cases, 500);
    fprintf(stderr, "total test cases run: %zu\n", test_cases);
}

GTEST_MAIN_RUN_ALL_TESTS()
