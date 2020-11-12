// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/instruction/index_lookup_table.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib::eval;

std::vector<uint32_t> make_table(std::vector<uint32_t> list) { return list; }

TEST(IndexLookupTableTest, single_dimension_lookup_table_is_correct)
{
    auto idx_fun = Function::parse({"x"}, "5-x");
    auto type = ValueType::from_spec("tensor(x[6])");
    auto table = IndexLookupTable::create(*idx_fun, type);

    EXPECT_EQ(IndexLookupTable::num_cached(), 1);
    EXPECT_EQ(IndexLookupTable::count_refs(), 1);
    EXPECT_EQ(table->get(), make_table({5,4,3,2,1,0}));
}

TEST(IndexLookupTableTest, dual_dimension_lookup_table_is_correct)
{
    auto idx_fun = Function::parse({"x","y"}, "5-(x*2+y)");
    auto type = ValueType::from_spec("tensor(x[3],y[2])");
    auto table = IndexLookupTable::create(*idx_fun, type);

    EXPECT_EQ(IndexLookupTable::num_cached(), 1);
    EXPECT_EQ(IndexLookupTable::count_refs(), 1);
    EXPECT_EQ(table->get(), make_table({5,4,3,2,1,0}));
}

TEST(IndexLookupTableTest, multi_dimension_lookup_table_is_correct)
{
    auto idx_fun = Function::parse({"a","b","c","d"}, "11-(a*6+b*2+c*2+d)");
    auto type = ValueType::from_spec("tensor(a[2],b[3],c[1],d[2])");
    auto table = IndexLookupTable::create(*idx_fun, type);

    EXPECT_EQ(IndexLookupTable::num_cached(), 1);
    EXPECT_EQ(IndexLookupTable::count_refs(), 1);
    EXPECT_EQ(table->get(), make_table({11,10,9,8,7,6,5,4,3,2,1,0}));
}

TEST(IndexLookupTableTest, lookup_tables_can_be_shared)
{
    auto idx_fun1 = Function::parse({"x"}, "5-x");
    auto type1 = ValueType::from_spec("tensor(x[6])");
    auto table1 = IndexLookupTable::create(*idx_fun1, type1);

    auto idx_fun2 = Function::parse({"x"}, "5-x");
    auto type2 = ValueType::from_spec("tensor(x[6])");
    auto table2 = IndexLookupTable::create(*idx_fun2, type2);

    EXPECT_EQ(IndexLookupTable::num_cached(), 1);
    EXPECT_EQ(IndexLookupTable::count_refs(), 2);
    EXPECT_EQ(&table1->get(), &table2->get());
    EXPECT_EQ(table1->get(), make_table({5,4,3,2,1,0}));
}

TEST(IndexLookupTableTest, lookup_tables_with_different_index_functions_are_not_shared)
{
    auto idx_fun1 = Function::parse({"x"}, "5-x");
    auto type1 = ValueType::from_spec("tensor(x[6])");
    auto table1 = IndexLookupTable::create(*idx_fun1, type1);

    auto idx_fun2 = Function::parse({"x"}, "x");
    auto type2 = ValueType::from_spec("tensor(x[6])");
    auto table2 = IndexLookupTable::create(*idx_fun2, type2);

    EXPECT_EQ(IndexLookupTable::num_cached(), 2);
    EXPECT_EQ(IndexLookupTable::count_refs(), 2);
    EXPECT_NE(&table1->get(), &table2->get());
    EXPECT_EQ(table1->get(), make_table({5,4,3,2,1,0}));
    EXPECT_EQ(table2->get(), make_table({0,1,2,3,4,5}));
}

TEST(IndexLookupTableTest, lookup_tables_with_different_value_types_are_not_shared)
{
    auto idx_fun1 = Function::parse({"x"}, "x");
    auto type1 = ValueType::from_spec("tensor(x[6])");
    auto table1 = IndexLookupTable::create(*idx_fun1, type1);

    auto idx_fun2 = Function::parse({"x"}, "x");
    auto type2 = ValueType::from_spec("tensor(x[5])");
    auto table2 = IndexLookupTable::create(*idx_fun2, type2);

    EXPECT_EQ(IndexLookupTable::num_cached(), 2);
    EXPECT_EQ(IndexLookupTable::count_refs(), 2);
    EXPECT_NE(&table1->get(), &table2->get());
    EXPECT_EQ(table1->get(), make_table({0,1,2,3,4,5}));
    EXPECT_EQ(table2->get(), make_table({0,1,2,3,4}));
}

TEST(IndexLookupTableTest, identical_lookup_tables_might_not_be_shared)
{
    auto idx_fun1 = Function::parse({"x"}, "5-x");
    auto type1 = ValueType::from_spec("tensor(x[6])");
    auto table1 = IndexLookupTable::create(*idx_fun1, type1);

    auto idx_fun2 = Function::parse({"x","y"}, "5-(x*2+y)");
    auto type2 = ValueType::from_spec("tensor(x[3],y[2])");
    auto table2 = IndexLookupTable::create(*idx_fun2, type2);

    EXPECT_EQ(IndexLookupTable::num_cached(), 2);
    EXPECT_EQ(IndexLookupTable::count_refs(), 2);
    EXPECT_NE(&table1->get(), &table2->get());
    EXPECT_EQ(table1->get(), make_table({5,4,3,2,1,0}));
    EXPECT_EQ(table2->get(), make_table({5,4,3,2,1,0}));
}

TEST(IndexLookupTableTest, unused_lookup_tables_are_discarded) {
    EXPECT_EQ(IndexLookupTable::num_cached(), 0);
    EXPECT_EQ(IndexLookupTable::count_refs(), 0);
}

GTEST_MAIN_RUN_ALL_TESTS()
