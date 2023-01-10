// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/data/smart_buffer.h>

using namespace vespalib;

void checkMemory(const vespalib::string &expect, const Memory &mem) {
    EXPECT_EQUAL(expect, vespalib::string(mem.data, mem.size));
}

void checkBuffer(const vespalib::string &expect, SmartBuffer &buf) {
    TEST_DO(checkMemory(expect, buf.obtain()));
}

void write_buf(const vespalib::string &str, SmartBuffer &buf) {
    WritableMemory mem = buf.reserve(str.size());
    for (size_t i = 0; i < str.size(); ++i) {
        mem.data[i] = str.data()[i];
    }
    buf.commit(str.size());
}

TEST("require that basic read/write works") {
    SmartBuffer buf(3);
    TEST_DO(checkBuffer("", buf));
    { // read from empty buffer
        EXPECT_TRUE(buf.empty());
        EXPECT_EQUAL(0u, buf.obtain().size);
    }
    { // write to buffer
        WritableMemory mem = buf.reserve(10);
        TEST_DO(checkBuffer("", buf));
        EXPECT_LESS_EQUAL(10u, mem.size);
        mem.data[0] = 'a';
        mem.data[1] = 'b';
        mem.data[2] = 'c';
        EXPECT_EQUAL(&buf, &buf.commit(3));
        EXPECT_FALSE(buf.empty());
        mem = buf.reserve(0);
        TEST_DO(checkBuffer("abc", buf));
        EXPECT_LESS_EQUAL(0u, mem.size);
    }
    { // read without evicting last byte
        Memory mem = buf.obtain();
        TEST_DO(checkBuffer("abc", buf));
        TEST_DO(checkMemory("abc", mem));
        EXPECT_EQUAL(&buf, &buf.evict(2));
        mem = buf.obtain();
        TEST_DO(checkBuffer("c", buf));
        TEST_DO(checkMemory("c", mem));
        mem = buf.obtain();
        TEST_DO(checkBuffer("c", buf));
        TEST_DO(checkMemory("c", mem));
    }
    { // write more to buffer
        WritableMemory mem = buf.reserve(10);
        EXPECT_LESS_EQUAL(10u, mem.size);
        TEST_DO(checkBuffer("c", buf));
        mem.data[0] = 'd';
        EXPECT_EQUAL(&buf, &buf.commit(1));
        mem = buf.reserve(5);
        TEST_DO(checkBuffer("cd", buf));
        EXPECT_LESS_EQUAL(5u, mem.size);
    }
    { // read until end
        EXPECT_FALSE(buf.empty());
        Memory mem = buf.obtain();
        TEST_DO(checkBuffer("cd", buf));
        TEST_DO(checkMemory("cd", mem));
        EXPECT_EQUAL(&buf, &buf.evict(1));
        mem = buf.obtain();
        TEST_DO(checkBuffer("d", buf));
        TEST_DO(checkMemory("d", mem));
        EXPECT_EQUAL(&buf, &buf.evict(1));
        EXPECT_TRUE(buf.empty());
        mem = buf.obtain();
        TEST_DO(checkBuffer("", buf));
        TEST_DO(checkMemory("", mem));
    }
}

TEST("require that requested initial size is not adjusted") {
    SmartBuffer buf(400);
    EXPECT_EQUAL(buf.capacity(), 400u);
}

TEST("require that buffer auto-resets when empty") {
    SmartBuffer buf(64);
    EXPECT_EQUAL(buf.reserve(10).size, 64u);
    EXPECT_TRUE(buf.empty());
    write_buf("abc", buf);
    EXPECT_FALSE(buf.empty());
    EXPECT_EQUAL(buf.reserve(10).size, 61u);
    buf.evict(3);
    EXPECT_TRUE(buf.empty());
    EXPECT_EQUAL(buf.reserve(10).size, 64u);
}

TEST("require that buffer can grow") {
    SmartBuffer buf(64);
    EXPECT_EQUAL(buf.capacity(), 64u);
    EXPECT_TRUE(buf.empty());
    write_buf("abc", buf);
    EXPECT_FALSE(buf.empty());
    write_buf("abc", buf);
    buf.evict(3);
    EXPECT_EQUAL(buf.reserve(70).size, size_t(128 - 3));
    TEST_DO(checkBuffer("abc", buf));
    EXPECT_EQUAL(buf.capacity(), 128u);
}

TEST("require that buffer can grow more than 2x") {
    SmartBuffer buf(64);
    EXPECT_EQUAL(buf.capacity(), 64u);
    EXPECT_TRUE(buf.empty());
    write_buf("abc", buf);
    EXPECT_FALSE(buf.empty());
    write_buf("abc", buf);
    buf.evict(3);
    EXPECT_EQUAL(buf.reserve(170).size, 170u);
    TEST_DO(checkBuffer("abc", buf));
    EXPECT_EQUAL(buf.capacity(), 173u);
}

TEST("require that buffer can be compacted") {
    SmartBuffer buf(16);
    EXPECT_EQUAL(buf.capacity(), 16u);
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
    EXPECT_EQUAL(buf.reserve(0).size, 1u);
    write_buf("abc", buf);
    TEST_DO(checkBuffer("abcabc", buf));
    EXPECT_EQUAL(buf.capacity(), 16u);
    EXPECT_EQUAL(buf.reserve(0).size, 10u);
}

TEST("require that a completely empty buffer can be created") {
    SmartBuffer buf(0);
    EXPECT_EQUAL(buf.capacity(), 0u);
    EXPECT_TRUE(buf.empty());
    EXPECT_TRUE(buf.obtain().data == nullptr);
}

TEST_MAIN() { TEST_RUN_ALL(); }
