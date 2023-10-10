// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/data/memory_input.h>
#include <vespa/vespalib/data/input_reader.h>
#include <vespa/vespalib/test/chunked_input.h>

using namespace vespalib;
using vespalib::test::ChunkedInput;

TEST("input reader smoke test") {
    const char *data = "abc\n"
                       "foo bar\n"
                       "2 + 2 = 4\n";
    MemoryInput memory_input(data);
    ChunkedInput input(memory_input, 3);
    {
        InputReader src(input);
        EXPECT_EQUAL(src.get_offset(), 0u);
        EXPECT_EQUAL(src.read(), 'a');
        EXPECT_EQUAL(src.read(), 'b');
        EXPECT_EQUAL(src.read(), 'c');
        EXPECT_EQUAL(src.read(), '\n');
        EXPECT_EQUAL(src.get_offset(), 4u);
        EXPECT_EQUAL(src.obtain(), 2u);
        EXPECT_EQUAL(src.read(8), Memory("foo bar\n"));
        EXPECT_EQUAL(src.get_offset(), 12u);
        EXPECT_EQUAL(src.obtain(), 3u);
        EXPECT_EQUAL(src.get_offset(), 12u);
        EXPECT_EQUAL(src.read(2), Memory("2 "));
        EXPECT_EQUAL(src.get_offset(), 14u);
        EXPECT_EQUAL(src.obtain(), 1u);
        EXPECT_EQUAL(src.read(8), Memory("+ 2 = 4\n"));
        EXPECT_TRUE(!src.failed());
        EXPECT_EQUAL(src.get_offset(), strlen(data));
        EXPECT_EQUAL(src.obtain(), 0u);
        EXPECT_TRUE(!src.failed());
        EXPECT_EQUAL(src.read(5), Memory());
        EXPECT_TRUE(src.failed());
        EXPECT_EQUAL(src.read(), '\0');
        EXPECT_EQUAL(src.obtain(), 0u);
        EXPECT_EQUAL(src.get_offset(), strlen(data));
        EXPECT_EQUAL(src.get_error_message(), vespalib::string("input underflow"));
    }
}

TEST("require that not reading everything leaves the input in appropriate state") {
    const char *data = "1234567890";
    MemoryInput input(data);
    {
        InputReader src(input);
        EXPECT_EQUAL(src.obtain(), 10u);
        EXPECT_EQUAL(src.read(5), Memory("12345"));
        EXPECT_EQUAL(input.obtain(), Memory("1234567890"));
    }
    EXPECT_EQUAL(input.obtain(), Memory("67890"));
}

TEST("require that input can be explicitly failed with custom message") {
    const char *data = "1234567890";
    MemoryInput input(data);
    {
        InputReader src(input);
        EXPECT_EQUAL(src.read(5), Memory("12345"));
        EXPECT_TRUE(!src.failed());
        src.fail("custom");
        EXPECT_TRUE(src.failed());
        EXPECT_EQUAL(src.read(), '\0');
        EXPECT_EQUAL(src.read(5), Memory());
        EXPECT_EQUAL(src.obtain(), 0u);
        src.fail("ignored");
        EXPECT_EQUAL(src.get_error_message(), vespalib::string("custom"));
        EXPECT_EQUAL(src.get_offset(), 5u);
    }
}

TEST("require that reading a byte sequence crossing the end of input fails") {
    const char *data = "1234567890";
    MemoryInput memory_input(data);
    ChunkedInput input(memory_input, 3);
    {
        InputReader src(input);
        EXPECT_EQUAL(src.read(15), Memory());
        EXPECT_TRUE(src.failed());
        EXPECT_EQUAL(src.get_error_message(), vespalib::string("input underflow"));
        EXPECT_EQUAL(src.get_offset(), 10u);        
    }
}

TEST("expect that obtain does not set failure state on input reader") {
    const char *data = "12345";
    for (bool byte_first: {true, false}) {
        MemoryInput input(data);
        InputReader src(input);
        EXPECT_EQUAL(src.obtain(), 5u);
        EXPECT_EQUAL(src.obtain(), 5u);
        EXPECT_EQUAL(src.read(5), Memory("12345"));
        EXPECT_TRUE(!src.failed());
        EXPECT_EQUAL(src.obtain(), 0u);
        EXPECT_EQUAL(src.obtain(), 0u);
        EXPECT_TRUE(!src.failed());
        if (byte_first) {
            EXPECT_EQUAL(src.read(), 0);
            EXPECT_TRUE(src.failed());
            EXPECT_EQUAL(src.read(5), Memory());
        } else {
            EXPECT_EQUAL(src.read(5), Memory());
            EXPECT_TRUE(src.failed());
            EXPECT_EQUAL(src.read(), 0);
        }
        EXPECT_EQUAL(src.get_error_message(), vespalib::string("input underflow"));
        EXPECT_EQUAL(src.obtain(), 0u);
    }
}

TEST("require that bytes can be unread when appropriate") {
    const char *data = "12345";
    MemoryInput memory_input(data);
    ChunkedInput input(memory_input, 3);
    InputReader src(input);
    EXPECT_TRUE(!src.try_unread());
    EXPECT_EQUAL(src.read(), '1');
    EXPECT_EQUAL(src.read(), '2');
    EXPECT_EQUAL(src.read(), '3');
    EXPECT_TRUE(src.try_unread());
    EXPECT_TRUE(src.try_unread());
    EXPECT_TRUE(src.try_unread());
    EXPECT_TRUE(!src.try_unread());
    EXPECT_EQUAL(src.read(), '1');
    EXPECT_EQUAL(src.read(), '2');
    EXPECT_EQUAL(src.read(), '3');
    EXPECT_EQUAL(src.read(), '4');
    EXPECT_TRUE(src.try_unread());
    EXPECT_TRUE(!src.try_unread());
    EXPECT_EQUAL(src.read(), '4');
    EXPECT_EQUAL(src.read(), '5');
    EXPECT_EQUAL(src.obtain(), 0u);
    EXPECT_TRUE(!src.try_unread());
    EXPECT_TRUE(!src.failed());
}

TEST("require that try read finds eof without failing the reader") {
    const char *data = "12345";
    MemoryInput memory_input(data);
    ChunkedInput input(memory_input, 3);
    InputReader src(input);
    EXPECT_EQUAL(src.try_read(), '1');
    EXPECT_EQUAL(src.try_read(), '2');
    EXPECT_EQUAL(src.try_read(), '3');
    EXPECT_EQUAL(src.try_read(), '4');
    EXPECT_EQUAL(src.try_read(), '5');
    EXPECT_TRUE(src.try_unread());
    EXPECT_EQUAL(src.try_read(), '5');
    EXPECT_EQUAL(src.try_read(), '\0');
    EXPECT_TRUE(!src.try_unread());
    EXPECT_TRUE(!src.failed());
}

TEST_MAIN() { TEST_RUN_ALL(); }
