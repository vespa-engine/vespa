// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/data/simple_buffer.h>
#include <vespa/vespalib/data/output_writer.h>

using namespace vespalib;

TEST(OutputWriterTest, output_writer_smoke_test) {
    SimpleBuffer buffer;
    {
        OutputWriter dst(buffer, 3);
        dst.write('a');
        dst.write('b');
        dst.write('c');
        dst.write('\n');
        dst.write("foo bar");
        dst.write('\n');
        dst.printf("%d + %d = %d\n", 2, 2, 4);
    }
    std::string expect = "abc\n"
                              "foo bar\n"
                              "2 + 2 = 4\n";
    EXPECT_EQ(Memory(expect), buffer.get());
}

TEST(OutputWriterTest, require_that_reserve_commit_works_as_expected) {
    SimpleBuffer buffer;
    {
        OutputWriter dst(buffer, 3);
        char *p = dst.reserve(5);
        p[0] = 'a';
        p[1] = 'b';
        p[2] = 'c';
        dst.commit(3);
        dst.reserve(1)[0] = '\n';
        dst.commit(1);
        dst.reserve(10);
    }
    std::string expect = "abc\n";
    EXPECT_EQ(Memory(expect), buffer.get());
}

TEST(OutputWriterTest, require_that_large_printf_works) {
    const char *str = "12345678901234567890123456789012345678901234567890"
                      "12345678901234567890123456789012345678901234567890"
                      "12345678901234567890123456789012345678901234567890"
                      "12345678901234567890123456789012345678901234567890";
    size_t str_len = strlen(str);
    EXPECT_EQ(str_len, 200u);
    SimpleBuffer buffer;
    {
        OutputWriter dst(buffer, 3);
        dst.printf("%s,%s,%s,%s", str, str, str, str);
    }
    ASSERT_EQ(buffer.get().size, (str_len * 4) + 3);
    EXPECT_EQ(buffer.get().data[str_len], ',');
    EXPECT_EQ(buffer.get().data[(2 * str_len) + 1], ',');
    EXPECT_EQ(buffer.get().data[(3 * str_len) + 2], ',');
    size_t offset = (buffer.get().size - str_len);
    EXPECT_EQ(Memory(buffer.get().data + offset, buffer.get().size - offset), Memory(str));
}

GTEST_MAIN_RUN_ALL_TESTS()
