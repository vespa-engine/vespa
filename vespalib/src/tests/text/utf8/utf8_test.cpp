// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/text/utf8.h>
#include <fcntl.h>
#include <unistd.h>


#include <vespa/log/log.h>
LOG_SETUP("utf8_test");
#if 0
#include <vespa/fastlib/text/unicodeutil.h>
#endif

using namespace vespalib;

TEST_SETUP(Test);

int
Test::Main()
{
    TEST_INIT("utf8_test");

    for (uint32_t h = 0; h < 0x1100; h++) {
        vespalib::string data;

        if (h >= 0xD8 && h < 0xE0) continue;

        Utf8Writer w(data);
        for (uint32_t i = 0; i < 256; i++) {
            unsigned int codepoint = (h << 8) | i;
            w.putChar(codepoint);
        }

        fprintf(stderr, "encoded 256 codepoints [U+%04X,U+%04X] in %zu bytes\n",
                (h << 8), (h << 8) | 0xFF, data.size());

        Utf8Reader r(data);
        for (uint32_t i = 0; i < 256; i++) {
            EXPECT_TRUE(r.hasMore());
            unsigned int codepoint = (h << 8) | i;
            unsigned int got = r.getChar(12345678);
            EXPECT_EQUAL(codepoint, got);
        }
        EXPECT_TRUE(! r.hasMore());

#if 0
        char *p = data.begin();
        char *e = data.end();
        for (uint32_t i = 0; i < 256; i++) {
            unsigned int codepoint = (h << 8) | i;
            unsigned int got = Fast_UnicodeUtil::GetUTF8Char(p);
            EXPECT_EQUAL(codepoint, got);
        }
        EXPECT_EQUAL(p, e);
#endif
    }

    {
        // read data produced from Java program
        int fd = ::open(TEST_PATH("regular-utf8.dat").c_str(), O_RDONLY);
        ASSERT_TRUE(fd > 0);
        vespalib::string data;
        data.clear();
        data.reserve(5510);
        ASSERT_TRUE(::read(fd, data.begin(), 5510) == 5509);
        data.append_from_reserved(5509);
        Utf8Reader r(data);
        uint32_t i = 32;
        uint32_t j = 3;
        while (i < 0x110000) {
            if (i < 0xD800 || i >= 0xE000) {
                ASSERT_TRUE(r.hasMore());
                uint32_t got = r.getChar(12345678);
                EXPECT_EQUAL(i, got);
            }
            i += j;
            j++;
        }
        EXPECT_TRUE(! r.hasMore());
    }
    TEST_DONE();
}
