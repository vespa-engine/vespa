// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/data/simple_buffer.h>
#include <vespa/vespalib/data/output_writer.h>

using namespace vespalib;

TEST("output writer smoke test") {
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
    vespalib::string expect = "abc\n"
                              "foo bar\n"
                              "2 + 2 = 4\n";
    EXPECT_EQUAL(Memory(expect), buffer.get());
}

TEST("require that reserve/commit works as expected") {
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
    vespalib::string expect = "abc\n";
    EXPECT_EQUAL(Memory(expect), buffer.get());
}

TEST("require that large printf works") {
    const char *str = "12345678901234567890123456789012345678901234567890"
                      "12345678901234567890123456789012345678901234567890"
                      "12345678901234567890123456789012345678901234567890"
                      "12345678901234567890123456789012345678901234567890";
    size_t str_len = strlen(str);
    EXPECT_EQUAL(str_len, 200u);
    SimpleBuffer buffer;
    {
        OutputWriter dst(buffer, 3);
        dst.printf("%s,%s,%s,%s", str, str, str, str);
    }
    ASSERT_EQUAL(buffer.get().size, (str_len * 4) + 3);
    EXPECT_EQUAL(buffer.get().data[str_len], ',');
    EXPECT_EQUAL(buffer.get().data[(2 * str_len) + 1], ',');
    EXPECT_EQUAL(buffer.get().data[(3 * str_len) + 2], ',');
    size_t offset = (buffer.get().size - str_len);
    EXPECT_EQUAL(Memory(buffer.get().data + offset, buffer.get().size - offset), Memory(str));
}

TEST_MAIN() { TEST_RUN_ALL(); }
