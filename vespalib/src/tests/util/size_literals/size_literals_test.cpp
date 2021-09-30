// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/gtest/gtest.h>

template<typename T> bool is_size_t(T) { return false; }
template<> bool is_size_t<size_t>(size_t) { return true; }

TEST(SizeLiteralsTest, simple_usage)
{
    auto v1k = 1_Ki;
    auto v1m = 1_Mi;
    auto v1g = 1_Gi;
    auto v1t = 1_Ti;
    auto v42k = 42_Ki;
    auto v42m = 42_Mi;
    auto v42g = 42_Gi;
    auto v42t = 42_Ti;

    EXPECT_EQ(v1k, 1024ul);
    EXPECT_EQ(v1m, 1024ul * 1024ul);
    EXPECT_EQ(v1g, 1024ul * 1024ul * 1024ul);
    EXPECT_EQ(v1t, 1024ul * 1024ul * 1024ul * 1024ul);;

    EXPECT_EQ(v42k, 42ul * 1024ul);
    EXPECT_EQ(v42m, 42ul * 1024ul * 1024ul);
    EXPECT_EQ(v42g, 42ul * 1024ul * 1024ul * 1024ul);
    EXPECT_EQ(v42t, 42ul * 1024ul * 1024ul * 1024ul * 1024ul);

    EXPECT_TRUE(is_size_t(v1k));
    EXPECT_TRUE(is_size_t(v1g));
    EXPECT_TRUE(is_size_t(v1g));
    EXPECT_TRUE(is_size_t(v1t));

    EXPECT_TRUE(is_size_t(v42k));
    EXPECT_TRUE(is_size_t(v42g));
    EXPECT_TRUE(is_size_t(v42g));
    EXPECT_TRUE(is_size_t(v42t));
}

GTEST_MAIN_RUN_ALL_TESTS()
