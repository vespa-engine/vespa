// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastlib/text/unicodeutil.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/testkit/test_master.hpp>

TEST("GetUTF8Char_WrongInput") {
    const char *testdata = "ab\xF8";

    ucs4_t the_char = 0;

    const unsigned char *src = reinterpret_cast<const unsigned char *>(testdata);
    while (*src != 0) {
        the_char = Fast_UnicodeUtil::GetUTF8Char(src);
    }
    EXPECT_EQUAL(Fast_UnicodeUtil::_BadUTF8Char, the_char);
}

TEST_MAIN() { TEST_RUN_ALL(); }
