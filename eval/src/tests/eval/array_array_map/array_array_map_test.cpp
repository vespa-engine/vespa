// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/array_array_map.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;

std::vector<int> ints(std::vector<int> vec) { return vec; }

template <typename T>
ConstArrayRef<T> refs(const std::vector<T> &vec) { return ConstArrayRef<T>(vec); }

//-----------------------------------------------------------------------------

TEST(ArrayArrayMapTest, simple_map_can_be_created_and_used) {
    // ctor params: 'keys_per_entry', 'values_per_entry', 'expected_entries'
    ArrayArrayMap<int,int> map(2,3,5);
    EXPECT_EQ(map.size(), 0);
    EXPECT_FALSE(map.lookup(refs(ints({1, 2}))).valid());
    auto tag = map.add_entry(refs(ints({1, 2})));
    EXPECT_EQ(map.size(), 1);
    auto values = map.get_values(tag);
    ASSERT_EQ(values.size(), 3);
    values[0] = 10;
    values[1] = 20;
    values[2] = 30;
    EXPECT_FALSE(map.lookup(refs(ints({2, 1}))).valid());
    auto tag2 = map.lookup(refs(ints({1, 2})));
    ASSERT_TRUE(tag2.valid());
    EXPECT_EQ(map.get_values(tag2)[1], 20);
}

TEST(ArrayArrayMapTest, lookup_or_add_entry_works) {
    ArrayArrayMap<int,int> map(2,3,5);
    auto res1 = map.lookup_or_add_entry(refs(ints({1, 2})));
    auto res2 = map.lookup_or_add_entry(refs(ints({1, 2})));
    EXPECT_TRUE(res1.second);
    EXPECT_FALSE(res2.second);
    EXPECT_EQ(map.get_values(res1.first).begin(), map.get_values(res2.first).begin());
    EXPECT_EQ(map.get_values(res1.first).size(), 3);
}

TEST(ArrayArrayMapTest, each_entry_works) {
    ArrayArrayMap<int,int> map(2,3,5);
    auto res1 = map.add_entry(refs(ints({1, 2})));
    auto res2 = map.add_entry(refs(ints({2, 1})));
    map.get_values(res1)[0] = 10;
    map.get_values(res2)[0] = 20;
    EXPECT_EQ(map.size(), 2);
    bool first = true;
    // Note: insert order is guaranteed here
    map.each_entry([&](const auto &keys, const auto &values)
                   {
                       if (first) {
                           EXPECT_EQ(keys[0], 1);
                           EXPECT_EQ(keys[1], 2);
                           EXPECT_EQ(values[0], 10);
                           first = false;
                       } else {
                           EXPECT_EQ(keys[0], 2);
                           EXPECT_EQ(keys[1], 1);
                           EXPECT_EQ(values[0], 20);
                       } 
                   });
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
