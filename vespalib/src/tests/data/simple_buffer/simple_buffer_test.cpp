// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/data/simple_buffer.h>

using namespace vespalib;

void checkMemory(const std::string &expect, const Memory &mem) {
    EXPECT_EQ(expect, mem.make_stringview());
}

void checkBuffer(const std::string &expect, const SimpleBuffer &buf) {
    GTEST_DO(checkMemory(expect, buf.get()));
}

TEST(SimpleBufferTest, simple_buffer) {
    SimpleBuffer buf;
    GTEST_DO(checkBuffer("", buf));
    { // read from empty buffer
        EXPECT_EQ(0u, buf.obtain().size);
    }
    { // write to buffer
        WritableMemory mem = buf.reserve(10);
        GTEST_DO(checkBuffer("", buf));
        EXPECT_EQ(10u, mem.size);
        mem.data[0] = 'a';
        mem.data[1] = 'b';
        mem.data[2] = 'c';
        EXPECT_EQ(&buf, &buf.commit(3));
        mem = buf.reserve(0);
        GTEST_DO(checkBuffer("abc", buf));
        EXPECT_EQ(0u, mem.size);
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
        EXPECT_EQ(10u, mem.size);
        GTEST_DO(checkBuffer("c", buf));
        mem.data[0] = 'd';
        EXPECT_EQ(&buf, &buf.commit(1));
        mem = buf.reserve(5);
        GTEST_DO(checkBuffer("cd", buf));
        EXPECT_EQ(5u, mem.size);
    }
    { // read until end
        Memory mem = buf.obtain();
        GTEST_DO(checkBuffer("cd", buf));
        GTEST_DO(checkMemory("cd", mem));
        EXPECT_EQ(&buf, &buf.evict(1));
        mem = buf.obtain();
        GTEST_DO(checkBuffer("d", buf));
        GTEST_DO(checkMemory("d", mem));
        EXPECT_EQ(&buf, &buf.evict(1));
        mem = buf.obtain();
        GTEST_DO(checkBuffer("", buf));
        GTEST_DO(checkMemory("", mem));
    }
}

TEST(SimpleBufferTest, require_that_add_works_as_expected) {
    SimpleBuffer buf;
    buf.add('a').add('b').add('c');
    EXPECT_EQ(buf.get(), Memory("abc"));
}

GTEST_MAIN_RUN_ALL_TESTS()
