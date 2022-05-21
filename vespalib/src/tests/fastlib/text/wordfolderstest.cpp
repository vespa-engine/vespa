// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastlib/text/normwordfolder.h>
#include <vespa/vespalib/testkit/test_kit.h>

TEST("NormalizeWordFolderConstruction") {
    Fast_NormalizeWordFolder::Setup(
            Fast_NormalizeWordFolder::DO_ACCENT_REMOVAL
            | Fast_NormalizeWordFolder::DO_KATAKANA_TO_HIRAGANA
            | Fast_NormalizeWordFolder::DO_SMALL_TO_NORMAL_KANA
            | Fast_NormalizeWordFolder::DO_SHARP_S_SUBSTITUTION
            | Fast_NormalizeWordFolder::DO_LIGATURE_SUBSTITUTION
            | Fast_NormalizeWordFolder::DO_MULTICHAR_EXPANSION);
}

TEST("TokenizeAnnotatedUCS4Buffer") {
    auto nwf = std::make_unique<Fast_NormalizeWordFolder>();
    const char *testinput = "This is a "
                            "\xEF\xBF\xB9" "café" "\xEF\xBF\xBA" "cafe" "\xEF\xBF\xBB"
                            " superduperextrafeaturecoolandlongplainword fun "
                            "\xEF\xBF\xB9" "www" "\xEF\xBF\xBA"
                            "world wide web extra long annotation block" "\xEF\xBF\xBB"
                            " test\nIt is cool.\n";
    const char *correct[] = {
            "this", "is", "a",
            "\xEF\xBF\xB9" "café" "\xEF\xBF\xBA" "cafe" "\xEF\xBF\xBB",
            "superduperextrafeaturecooland", "fun",
            "\xEF\xBF\xB9" "www" "\xEF\xBF\xBA" "world wide web extra lon",
            "test", "it", "is", "cool" };

    const char *teststart = testinput;
    const char *testend = testinput + strlen(testinput);
    ucs4_t destbuf[32];
    ucs4_t *destbufend = destbuf + 32;

    const char *origstart = testinput;
    size_t tokenlen = 0;

    int tokencounter = 0;
    while ((teststart = nwf->UCS4Tokenize(teststart, testend, destbuf, destbufend, origstart, tokenlen)) < testend) {
        EXPECT_EQUAL(0, Fast_UnicodeUtil::utf8cmp(correct[tokencounter++], destbuf));
    }

}

TEST_MAIN() { TEST_RUN_ALL(); }