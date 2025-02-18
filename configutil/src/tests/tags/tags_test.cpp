// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <lib/tags.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace configdefinitions;

TEST(TagsTest, upcase)
{
    EXPECT_EQ(std::string("A"), upcase(std::string("a")));
    EXPECT_EQ(std::string("A"), upcase(std::string("A")));
}

TEST(TagsTest, tagsContain)
{
    EXPECT_TRUE(tagsContain("a b c", "a"));
    EXPECT_TRUE(tagsContain("a b c", "b"));
    EXPECT_TRUE(tagsContain("a b c", "c"));

    EXPECT_FALSE(tagsContain("a b c", "d"));
}

GTEST_MAIN_RUN_ALL_TESTS()
