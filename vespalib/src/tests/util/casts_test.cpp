// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/casts.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;

TEST(CastsTest, test_char_pointer_casts)
{
    char ca[5] = "foo1";
    unsigned char ua[5] = "foo2";

    char *cp = ca;
    unsigned char *up = ua;
    const char *ccp = "foo3";
    const unsigned char *cup = up;

    unsigned char *t1 = char_p_cast<unsigned char>(cp);
    const unsigned char *t2 = char_p_cast<unsigned char>(ccp);

    char *t3 = char_p_cast<char>(up);
    const char *t4 = char_p_cast<char>(cup);

    EXPECT_EQ((char *)t1, cp);
    EXPECT_EQ((const char *)t2, ccp);
    EXPECT_EQ(t3, (char *)up);
    EXPECT_EQ(t4, (const char *)cup);

    auto t5 = char_p_cast<char>(up);
    auto t6 = char_p_cast<char>(cup);
    EXPECT_TRUE((std::same_as<decltype(t5), char *>));
    EXPECT_TRUE((std::same_as<decltype(t6), const char *>));
}

TEST(CastsTest, test_u8_literal)
{
    constexpr const char8_t *one = u8"Blåbær før München";
    constexpr const char *two = u8"Blåbær før München"_C;
    for (int i = 0; one[i] != 0; i++) {
        EXPECT_EQ(uint8_t(one[i]), uint8_t(two[i]));
    }
    EXPECT_GT(strlen(two), 20);
}
