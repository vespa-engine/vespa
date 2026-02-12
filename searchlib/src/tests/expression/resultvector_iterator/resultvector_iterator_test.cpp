// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/expression/resultvector.h>
#include <vespa/searchlib/expression/integerresultnode.h>
#include <vespa/searchlib/expression/stringresultnode.h>
#include <vespa/searchlib/expression/floatresultnode.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <algorithm>

using namespace search::expression;

class ResultVectorIteratorTest : public ::testing::Test {
protected:
    static Int64ResultNodeVector create_int_vector(const std::vector<int64_t>& values) {
        Int64ResultNodeVector vec;
        for (const auto val : values) {
            vec.push_back(Int64ResultNode(val));
        }
        return vec;
    }

    static StringResultNodeVector create_string_vector(const std::vector<std::string>& values) {
        StringResultNodeVector vec;
        for (const auto& val : values) {
            vec.push_back(StringResultNode(val));
        }
        return vec;
    }
};

TEST_F(ResultVectorIteratorTest, test_basic_iteration) {
    auto vec = create_int_vector({1, 2, 3, 4, 5});

    // Test range-based for loop with non-const iterator
    std::vector<int64_t> collected;
    for (auto& node : vec) {
        collected.push_back(node.getInteger());
    }

    EXPECT_EQ(collected.size(), 5);
    EXPECT_EQ(collected[0], 1);
    EXPECT_EQ(collected[1], 2);
    EXPECT_EQ(collected[2], 3);
    EXPECT_EQ(collected[3], 4);
    EXPECT_EQ(collected[4], 5);
}

TEST_F(ResultVectorIteratorTest, test_const_iteration) {
    const auto vec = create_int_vector({10, 20, 30});

    // Test range-based for loop with const iterator
    std::vector<int64_t> collected;
    for (const auto& node : vec) {
        collected.push_back(node.getInteger());
    }

    EXPECT_EQ(collected.size(), 3);
    EXPECT_EQ(collected[0], 10);
    EXPECT_EQ(collected[1], 20);
    EXPECT_EQ(collected[2], 30);
}

TEST_F(ResultVectorIteratorTest, test_empty_vector) {
    Int64ResultNodeVector vec;

    // Test that iteration over empty vector works
    int count = 0;
    for (const auto& node : vec) {
        (void)node;
        count++;
    }

    EXPECT_EQ(count, 0);
}

TEST_F(ResultVectorIteratorTest, test_string_vector_iteration) {
    auto vec = create_string_vector({"hello", "world", "test"});

    std::vector<std::string> collected;
    for (const auto& node : vec) {
        const auto& str_node = static_cast<const StringResultNode&>(node);
        collected.push_back(str_node.get());
    }

    EXPECT_EQ(collected.size(), 3);
    EXPECT_EQ(collected[0], "hello");
    EXPECT_EQ(collected[1], "world");
    EXPECT_EQ(collected[2], "test");
}

TEST_F(ResultVectorIteratorTest, test_iterator_equality) {
    auto vec = create_int_vector({1, 2, 3});

    const auto it1 = vec.begin();
    const auto it2 = vec.begin();
    const auto end = vec.end();

    EXPECT_TRUE(it1 == it2);
    EXPECT_FALSE(it1 != it2);
    EXPECT_FALSE(it1 == end);
    EXPECT_TRUE(it1 != end);
}

TEST_F(ResultVectorIteratorTest, test_polymorphic_iteration) {
    auto vec = create_int_vector({1, 2, 3, 4, 5});
    ResultNodeVector* poly_vec = &vec;

    // Test that we can iterate through the polymorphic base class pointer
    std::vector<int64_t> collected;
    for (ResultNode& node : *poly_vec) {
        collected.push_back(node.getInteger());
    }

    EXPECT_EQ(collected.size(), 5);
    EXPECT_EQ(collected[0], 1);
    EXPECT_EQ(collected[4], 5);
}

TEST_F(ResultVectorIteratorTest, test_modification_through_iterator) {
    auto vec = create_int_vector({1, 2, 3});

    // Modify values through non-const iterator
    for (auto& node : vec) {
        auto& int_node = static_cast<Int64ResultNode&>(node);
        int_node.set(int_node.getInteger() * 2);
    }

    // Verify modification
    EXPECT_EQ(vec.get(0).getInteger(), 2);
    EXPECT_EQ(vec.get(1).getInteger(), 4);
    EXPECT_EQ(vec.get(2).getInteger(), 6);
}

GTEST_MAIN_RUN_ALL_TESTS()
