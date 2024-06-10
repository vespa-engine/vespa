// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vsm/common/charbuffer.h>

namespace vsm {

TEST(CharBufferTest, empty)
{
    CharBuffer buf;
    EXPECT_EQ(buf.getLength(), 0u);
    EXPECT_EQ(buf.getPos(), 0u);
    EXPECT_EQ(buf.getRemaining(), 0u);
}

TEST(CharBufferTest, explicit_length)
{
    CharBuffer buf(8);
    EXPECT_EQ(buf.getLength(), 8u);
    EXPECT_EQ(buf.getPos(), 0u);
    EXPECT_EQ(buf.getRemaining(), 8u);
}

TEST(CharBufferTest, resize)
{
    CharBuffer buf(8);
    EXPECT_EQ(buf.getLength(), 8u);
    buf.resize(16);
    EXPECT_EQ(buf.getLength(), 16u);
    buf.resize(8);
    EXPECT_EQ(buf.getLength(), 16u);
}

TEST(CharBufferTest, put_with_triggered_resize)
{
    CharBuffer buf(8);
    buf.put("123456", 6);
    EXPECT_EQ(buf.getLength(), 8u);
    EXPECT_EQ(buf.getPos(), 6u);
    EXPECT_EQ(buf.getRemaining(), 2u);
    EXPECT_EQ(std::string(buf.getBuffer(), buf.getPos()), "123456");
    buf.put("789", 3);
    EXPECT_EQ(buf.getLength(), 12u);
    EXPECT_EQ(buf.getPos(), 9u);
    EXPECT_EQ(buf.getRemaining(), 3u);
    EXPECT_EQ(std::string(buf.getBuffer(), buf.getPos()), "123456789");
    buf.put('a');
    EXPECT_EQ(buf.getLength(), 12u);
    EXPECT_EQ(buf.getPos(), 10u);
    EXPECT_EQ(buf.getRemaining(), 2u);
    EXPECT_EQ(std::string(buf.getBuffer(), buf.getPos()), "123456789a");
    buf.reset();
    EXPECT_EQ(buf.getLength(), 12u);
    EXPECT_EQ(buf.getPos(), 0u);
    EXPECT_EQ(buf.getRemaining(), 12u);
    buf.put("bcd", 3);
    EXPECT_EQ(buf.getLength(), 12u);
    EXPECT_EQ(buf.getPos(), 3u);
    EXPECT_EQ(buf.getRemaining(), 9u);
    EXPECT_EQ(std::string(buf.getBuffer(), buf.getPos()), "bcd");
}

}

GTEST_MAIN_RUN_ALL_TESTS()
