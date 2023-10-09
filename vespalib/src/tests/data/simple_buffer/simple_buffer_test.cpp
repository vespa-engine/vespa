// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/data/simple_buffer.h>

using namespace vespalib;

void checkMemory(const string &expect, const Memory &mem) {
    EXPECT_EQUAL(expect, string(mem.data, mem.size));
}

void checkBuffer(const string &expect, const SimpleBuffer &buf) {
    TEST_DO(checkMemory(expect, buf.get()));
}

TEST("simple buffer") {
    SimpleBuffer buf;
    TEST_DO(checkBuffer("", buf));
    { // read from empty buffer
        EXPECT_EQUAL(0u, buf.obtain().size);
    }
    { // write to buffer
        WritableMemory mem = buf.reserve(10);
        TEST_DO(checkBuffer("", buf));
        EXPECT_EQUAL(10u, mem.size);
        mem.data[0] = 'a';
        mem.data[1] = 'b';
        mem.data[2] = 'c';
        EXPECT_EQUAL(&buf, &buf.commit(3));
        mem = buf.reserve(0);
        TEST_DO(checkBuffer("abc", buf));
        EXPECT_EQUAL(0u, mem.size);
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
        EXPECT_EQUAL(10u, mem.size);
        TEST_DO(checkBuffer("c", buf));
        mem.data[0] = 'd';
        EXPECT_EQUAL(&buf, &buf.commit(1));
        mem = buf.reserve(5);
        TEST_DO(checkBuffer("cd", buf));
        EXPECT_EQUAL(5u, mem.size);
    }
    { // read until end
        Memory mem = buf.obtain();
        TEST_DO(checkBuffer("cd", buf));
        TEST_DO(checkMemory("cd", mem));
        EXPECT_EQUAL(&buf, &buf.evict(1));
        mem = buf.obtain();
        TEST_DO(checkBuffer("d", buf));
        TEST_DO(checkMemory("d", mem));
        EXPECT_EQUAL(&buf, &buf.evict(1));
        mem = buf.obtain();
        TEST_DO(checkBuffer("", buf));
        TEST_DO(checkMemory("", mem));
    }
}

TEST("require that add works as expected") {
    SimpleBuffer buf;
    buf.add('a').add('b').add('c');
    EXPECT_EQUAL(buf.get(), Memory("abc"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
