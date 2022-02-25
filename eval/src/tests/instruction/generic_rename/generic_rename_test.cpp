// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/instruction/generic_rename.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/eval/test/reference_operations.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::instruction;
using namespace vespalib::eval::test;

using vespalib::make_string_short::fmt;

GenSpec G() { return GenSpec(); }

const std::vector<GenSpec> rename_layouts = {
    G().idx("x", 3),
    G().idx("x", 3).idx("y", 5),
    G().idx("x", 3).idx("y", 5).idx("z", 7),
    G().map("x", {"a","b","c"}),
    G().map("x", {"a","b","c"}).map("y", {"foo","bar"}),
    G().map("x", {"a","b","c"}).map("y", {"foo","bar"}).map("z", {"i","j","k","l"}),
    G().idx("x", 3).map("y", {"foo", "bar"}).idx("z", 7),
    G().map("x", {"a","b","c"}).idx("y", 5).map("z", {"i","j","k","l"})
};

struct FromTo {
    std::vector<vespalib::string> from;
    std::vector<vespalib::string> to;

    FromTo(const std::vector<vespalib::string>& from_in, const std::vector<vespalib::string>& to_in)
        : from(from_in),
          to(to_in)
    {
    }
};

std::vector<FromTo> rename_from_to = {
    { {"x"}, {"x_renamed"} },
    { {"x"}, {"z_was_x"} },
    { {"x", "y"}, {"y", "x"} },
    { {"x", "z"}, {"z", "x"} },
    { {"x", "y", "z"}, {"a", "b", "c"} },
    { {"z"}, {"a"} },
    { {"y"}, {"z_was_y"} },
    { {"y"}, {"b"} }
};


TEST(GenericRenameTest, dense_rename_plan_can_be_created_and_executed) {
    auto lhs = ValueType::from_spec("tensor(a[2],c[3],d{},e[5],g[7],h{})");
    std::vector<vespalib::string> from({"a", "c", "e"});
    std::vector<vespalib::string>   to({"f", "a", "b"});
    ValueType renamed = lhs.rename(from, to);
    auto plan = DenseRenamePlan(lhs, renamed, from, to);
    SmallVector<size_t> expect_loop = {15,2,7};
    SmallVector<size_t> expect_stride = {7,105,1};
    EXPECT_EQ(plan.subspace_size, 210);
    EXPECT_EQ(plan.loop_cnt, expect_loop);
    EXPECT_EQ(plan.stride, expect_stride);
    std::vector<int> out;
    int want[3][5][2][7];
    size_t counter = 0;
    for (size_t a = 0; a < 2; ++a) {
        for (size_t c = 0; c < 3; ++c) {
            for (size_t e = 0; e < 5; ++e) {
                for (size_t g = 0; g < 7; ++g) {
                    want[c][e][a][g] = counter++;
                }
            }
        }
    }
    std::vector<int> expect(210);
    memcpy(&expect[0], &want[0], 210*sizeof(int));
    auto move_cell = [&](size_t offset) { out.push_back(offset); };
    plan.execute(0, move_cell);
    EXPECT_EQ(out, expect);
}

TEST(GenericRenameTest, sparse_rename_plan_can_be_created) {
    auto lhs = ValueType::from_spec("tensor(a{},c{},d[3],e{},g{},h[5])");
    std::vector<vespalib::string> from({"a", "c", "e"});
    std::vector<vespalib::string>   to({"f", "a", "b"});
    ValueType renamed = lhs.rename(from, to);
    auto plan = SparseRenamePlan(lhs, renamed, from, to);
    EXPECT_EQ(plan.mapped_dims, 4);
    SmallVector<size_t> expect = {2,0,1,3};
    EXPECT_EQ(plan.output_dimensions, expect);
}

vespalib::string rename_dimension(const vespalib::string &name, const FromTo &ft) {
    assert(ft.from.size() == ft.to.size());
    for (size_t i = 0; i < ft.from.size(); ++i) {
        if (name == ft.from[i]) {
            return ft.to[i];
        }
    }
    return name;
}

TensorSpec perform_generic_rename(const TensorSpec &a,
                                  const FromTo &ft, const ValueBuilderFactory &factory)
{
    Stash stash;
    auto lhs = value_from_spec(a, factory);
    auto res_type = lhs->type().rename(ft.from, ft.to);
    auto my_op = GenericRename::make_instruction(res_type, lhs->type(), ft.from, ft.to, factory, stash);
    InterpretedFunction::EvalSingle single(factory, my_op);
    return spec_from_value(single.eval(std::vector<Value::CREF>({*lhs})));
}

void test_generic_rename_with(const ValueBuilderFactory &factory) {
    for (const auto &layout : rename_layouts) {
        for (CellType ct : CellTypeUtils::list_types()) {
            auto lhs = layout.cpy().cells(ct);
            ValueType lhs_type = lhs.type();
            for (const auto & from_to : rename_from_to) {
                ValueType renamed_type = lhs_type.rename(from_to.from, from_to.to);
                if (renamed_type.is_error()) continue;
                // printf("type %s -> %s\n", lhs_type.to_spec().c_str(), renamed_type.to_spec().c_str());
                SCOPED_TRACE(fmt("\n===\nLHS: %s\n===\n", lhs.gen().to_string().c_str()));
                auto expect = ReferenceOperations::rename(lhs, from_to.from, from_to.to);
                auto actual = perform_generic_rename(lhs, from_to, factory);
                EXPECT_EQ(actual, expect);
            }
        }
    }
}

TEST(GenericRenameTest, generic_rename_works_for_simple_values) {
    test_generic_rename_with(SimpleValueBuilderFactory::get());
}

TEST(GenericRenameTest, generic_rename_works_for_fast_values) {
    test_generic_rename_with(FastValueBuilderFactory::get());
}

GTEST_MAIN_RUN_ALL_TESTS()
