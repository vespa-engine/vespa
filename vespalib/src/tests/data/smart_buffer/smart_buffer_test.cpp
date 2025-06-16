// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/data/smart_buffer.h>

using namespace vespalib;

void checkMemory(const std::string &expect, const Memory &mem) {
    EXPECT_EQ(expect, std::string(mem.data, mem.size));
}

void checkBuffer(const std::string &expect, SmartBuffer &buf) {
    GTEST_DO(checkMemory(expect, buf.obtain()));
}

void write_buf(const std::string &str, SmartBuffer &buf) {
    WritableMemory mem = buf.reserve(str.size());
    for (size_t i = 0; i < str.size(); ++i) {
        mem.data[i] = str.data()[i];
    }
    buf.commit(str.size());
}

TEST(SmartBufferTest, require_that_basic_read_write_works) {
    SmartBuffer buf(3);
    GTEST_DO(checkBuffer("", buf));
    { // read from empty buffer
        EXPECT_TRUE(buf.empty());
        EXPECT_EQ(0u, buf.obtain().size);
    }
    { // write to buffer
        WritableMemory mem = buf.reserve(10);
        GTEST_DO(checkBuffer("", buf));
        EXPECT_LE(10u, mem.size);
        mem.data[0] = 'a';
        mem.data[1] = 'b';
        mem.data[2] = 'c';
        EXPECT_EQ(&buf, &buf.commit(3));
        EXPECT_FALSE(buf.empty());
        mem = buf.reserve(0);
        GTEST_DO(checkBuffer("abc", buf));
        EXPECT_LE(0u, mem.size);
    }
    { // read without evicting last byte
        Memory mem = buf.obtain();
        GTEST_DO(checkBuffer("abc", buf));
        GTEST_DO(checkMemory("abc", mem));
        EXPECT_EQ(&buf, &buf.evict(2));
        mem = buf.obtain();
        GTEST_DO(checkBuffer("c", buf));
        GTEST_DO(checkMemory("c", mem));
        mem = buf.obtain();
        GTEST_DO(checkBuffer("c", buf));
        GTEST_DO(checkMemory("c", mem));
    }
    { // write more to buffer
        WritableMemory mem = buf.reserve(10);
        EXPECT_LE(10u, mem.size);
        GTEST_DO(checkBuffer("c", buf));
        mem.data[0] = 'd';
        EXPECT_EQ(&buf, &buf.commit(1));
        mem = buf.reserve(5);
        GTEST_DO(checkBuffer("cd", buf));
        EXPECT_LE(5u, mem.size);
    }
    { // read until end
        EXPECT_FALSE(buf.empty());
        Memory mem = buf.obtain();
        GTEST_DO(checkBuffer("cd", buf));
        GTEST_DO(checkMemory("cd", mem));
        EXPECT_EQ(&buf, &buf.evict(1));
        mem = buf.obtain();
        GTEST_DO(checkBuffer("d", buf));
        GTEST_DO(checkMemory("d", mem));
        EXPECT_EQ(&buf, &buf.evict(1));
        EXPECT_TRUE(buf.empty());
        mem = buf.obtain();
        GTEST_DO(checkBuffer("", buf));
        GTEST_DO(checkMemory("", mem));
    }
}

TEST(SmartBufferTest, require_that_requested_initial_size_is_not_adjusted) {
    SmartBuffer buf(400);
    EXPECT_EQ(buf.capacity(), 400u);
}

TEST(SmartBufferTest, require_that_buffer_auto_resets_when_empty) {
    SmartBuffer buf(64);
    EXPECT_EQ(buf.reserve(10).size, 64u);
    EXPECT_TRUE(buf.empty());
    write_buf("abc", buf);
    EXPECT_FALSE(buf.empty());
    EXPECT_EQ(buf.reserve(10).size, 61u);
    buf.evict(3);
    EXPECT_TRUE(buf.empty());
    EXPECT_EQ(buf.reserve(10).size, 64u);
}

TEST(SmartBufferTest, require_that_buffer_can_grow) {
    SmartBuffer buf(64);
    EXPECT_EQ(buf.capacity(), 64u);
    EXPECT_TRUE(buf.empty());
    write_buf("abc", buf);
    EXPECT_FALSE(buf.empty());
    write_buf("abc", buf);
    buf.evict(3);
    EXPECT_EQ(buf.reserve(70).size, size_t(128 - 3));
    GTEST_DO(checkBuffer("abc", buf));
    EXPECT_EQ(buf.capacity(), 128u);
}

TEST(SmartBufferTest, require_that_buffer_can_grow_more_than_2x) {
    SmartBuffer buf(64);
    EXPECT_EQ(buf.capacity(), 64u);
    EXPECT_TRUE(buf.empty());
    write_buf("abc", buf);
    EXPECT_FALSE(buf.empty());
    write_buf("abc", buf);
    buf.evict(3);
    EXPECT_EQ(buf.reserve(170).size, 170u);
    GTEST_DO(checkBuffer("abc", buf));
    EXPECT_EQ(buf.capacity(), 173u);
}

TEST(SmartBufferTest, require_that_buffer_can_be_compacted) {
    SmartBuffer buf(16);
    EXPECT_EQ(buf.capacity(), 16u);
    EXPECT_TRUE(buf.empty());
    write_buf("abc", buf);
    EXPECT_FALSE(buf.empty());
    write_buf("abc", buf);
    buf.evict(3);
    write_buf("abc", buf);
    buf.evict(3);
    write_buf("abc", buf);
    buf.evict(3);
    write_buf("abc", buf);
    buf.evict(3);
    EXPECT_EQ(buf.reserve(0).size, 1u);
    write_buf("abc", buf);
    GTEST_DO(checkBuffer("abcabc", buf));
    EXPECT_EQ(buf.capacity(), 16u);
    EXPECT_EQ(buf.reserve(0).size, 10u);
}

TEST(SmartBufferTest, require_that_a_completely_empty_buffer_can_be_created) {
    SmartBuffer buf(0);
    EXPECT_EQ(buf.capacity(), 0u);
    EXPECT_TRUE(buf.empty());
    EXPECT_TRUE(buf.obtain().data == nullptr);
}

GTEST_MAIN_RUN_ALL_TESTS()
