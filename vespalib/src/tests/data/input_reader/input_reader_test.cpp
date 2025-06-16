// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/data/memory_input.h>
#include <vespa/vespalib/data/input_reader.h>
#include <vespa/vespalib/test/chunked_input.h>

using namespace vespalib;
using vespalib::test::ChunkedInput;

TEST(InputReaderTest, input_reader_smoke_test) {
    const char *data = "abc\n"
                       "foo bar\n"
                       "2 + 2 = 4\n";
    MemoryInput memory_input(data);
    ChunkedInput input(memory_input, 3);
    {
        InputReader src(input);
        EXPECT_EQ(src.get_offset(), 0u);
        EXPECT_EQ(src.read(), 'a');
        EXPECT_EQ(src.read(), 'b');
        EXPECT_EQ(src.read(), 'c');
        EXPECT_EQ(src.read(), '\n');
        EXPECT_EQ(src.get_offset(), 4u);
        EXPECT_EQ(src.obtain(), 2u);
        EXPECT_EQ(src.read(8), Memory("foo bar\n"));
        EXPECT_EQ(src.get_offset(), 12u);
        EXPECT_EQ(src.obtain(), 3u);
        EXPECT_EQ(src.get_offset(), 12u);
        EXPECT_EQ(src.read(2), Memory("2 "));
        EXPECT_EQ(src.get_offset(), 14u);
        EXPECT_EQ(src.obtain(), 1u);
        EXPECT_EQ(src.read(8), Memory("+ 2 = 4\n"));
        EXPECT_TRUE(!src.failed());
        EXPECT_EQ(src.get_offset(), strlen(data));
        EXPECT_EQ(src.obtain(), 0u);
        EXPECT_TRUE(!src.failed());
        EXPECT_EQ(src.read(5), Memory());
        EXPECT_TRUE(src.failed());
        EXPECT_EQ(src.read(), '\0');
        EXPECT_EQ(src.obtain(), 0u);
        EXPECT_EQ(src.get_offset(), strlen(data));
        EXPECT_EQ(src.get_error_message(), std::string("input underflow"));
    }
}

TEST(InputReaderTest, require_that_not_reading_everything_leaves_the_input_in_appropriate_state) {
    const char *data = "1234567890";
    MemoryInput input(data);
    {
        InputReader src(input);
        EXPECT_EQ(src.obtain(), 10u);
        EXPECT_EQ(src.read(5), Memory("12345"));
        EXPECT_EQ(input.obtain(), Memory("1234567890"));
    }
    EXPECT_EQ(input.obtain(), Memory("67890"));
}

TEST(InputReaderTest, require_that_input_can_be_explicitly_failed_with_custom_message) {
    const char *data = "1234567890";
    MemoryInput input(data);
    {
        InputReader src(input);
        EXPECT_EQ(src.read(5), Memory("12345"));
        EXPECT_TRUE(!src.failed());
        src.fail("custom");
        EXPECT_TRUE(src.failed());
        EXPECT_EQ(src.read(), '\0');
        EXPECT_EQ(src.read(5), Memory());
        EXPECT_EQ(src.obtain(), 0u);
        src.fail("ignored");
        EXPECT_EQ(src.get_error_message(), std::string("custom"));
        EXPECT_EQ(src.get_offset(), 5u);
    }
}

TEST(InputReaderTest, require_that_reading_a_byte_sequence_crossing_the_end_of_input_fails) {
    const char *data = "1234567890";
    MemoryInput memory_input(data);
    ChunkedInput input(memory_input, 3);
    {
        InputReader src(input);
        EXPECT_EQ(src.read(15), Memory());
        EXPECT_TRUE(src.failed());
        EXPECT_EQ(src.get_error_message(), std::string("input underflow"));
        EXPECT_EQ(src.get_offset(), 10u);        
    }
}

TEST(InputReaderTest, expect_that_obtain_does_not_set_failure_state_on_input_reader) {
    const char *data = "12345";
    for (bool byte_first: {true, false}) {
        MemoryInput input(data);
        InputReader src(input);
        EXPECT_EQ(src.obtain(), 5u);
        EXPECT_EQ(src.obtain(), 5u);
        EXPECT_EQ(src.read(5), Memory("12345"));
        EXPECT_TRUE(!src.failed());
        EXPECT_EQ(src.obtain(), 0u);
        EXPECT_EQ(src.obtain(), 0u);
        EXPECT_TRUE(!src.failed());
        if (byte_first) {
            EXPECT_EQ(src.read(), 0);
            EXPECT_TRUE(src.failed());
            EXPECT_EQ(src.read(5), Memory());
        } else {
            EXPECT_EQ(src.read(5), Memory());
            EXPECT_TRUE(src.failed());
            EXPECT_EQ(src.read(), 0);
        }
        EXPECT_EQ(src.get_error_message(), std::string("input underflow"));
        EXPECT_EQ(src.obtain(), 0u);
    }
}

TEST(InputReaderTest, require_that_bytes_can_be_unread_when_appropriate) {
    const char *data = "12345";
    MemoryInput memory_input(data);
    ChunkedInput input(memory_input, 3);
    InputReader src(input);
    EXPECT_TRUE(!src.try_unread());
    EXPECT_EQ(src.read(), '1');
    EXPECT_EQ(src.read(), '2');
    EXPECT_EQ(src.read(), '3');
    EXPECT_TRUE(src.try_unread());
    EXPECT_TRUE(src.try_unread());
    EXPECT_TRUE(src.try_unread());
    EXPECT_TRUE(!src.try_unread());
    EXPECT_EQ(src.read(), '1');
    EXPECT_EQ(src.read(), '2');
    EXPECT_EQ(src.read(), '3');
    EXPECT_EQ(src.read(), '4');
    EXPECT_TRUE(src.try_unread());
    EXPECT_TRUE(!src.try_unread());
    EXPECT_EQ(src.read(), '4');
    EXPECT_EQ(src.read(), '5');
    EXPECT_EQ(src.obtain(), 0u);
    EXPECT_TRUE(!src.try_unread());
    EXPECT_TRUE(!src.failed());
}

TEST(InputReaderTest, require_that_try_read_finds_eof_without_failing_the_reader) {
    const char *data = "12345";
    MemoryInput memory_input(data);
    ChunkedInput input(memory_input, 3);
    InputReader src(input);
    EXPECT_EQ(src.try_read(), '1');
    EXPECT_EQ(src.try_read(), '2');
    EXPECT_EQ(src.try_read(), '3');
    EXPECT_EQ(src.try_read(), '4');
    EXPECT_EQ(src.try_read(), '5');
    EXPECT_TRUE(src.try_unread());
    EXPECT_EQ(src.try_read(), '5');
    EXPECT_EQ(src.try_read(), '\0');
    EXPECT_TRUE(!src.try_unread());
    EXPECT_TRUE(!src.failed());
}

GTEST_MAIN_RUN_ALL_TESTS()
