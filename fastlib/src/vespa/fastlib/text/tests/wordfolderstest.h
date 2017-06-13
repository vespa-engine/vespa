// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastlib/testsuite/test.h>
#include <vespa/fastlib/text/normwordfolder.h>
#include <vespa/fastos/app.h>
#include <memory>
#include <cstring>

class WordFoldersTest : public Test
{
    bool NormalizeWordFolderConstruction() {
        Fast_NormalizeWordFolder::Setup(
                Fast_NormalizeWordFolder::DO_ACCENT_REMOVAL
                | Fast_NormalizeWordFolder::DO_KATAKANA_TO_HIRAGANA
                | Fast_NormalizeWordFolder::DO_SMALL_TO_NORMAL_KANA
                | Fast_NormalizeWordFolder::DO_SHARP_S_SUBSTITUTION
                | Fast_NormalizeWordFolder::DO_LIGATURE_SUBSTITUTION
                | Fast_NormalizeWordFolder::DO_MULTICHAR_EXPANSION);

        Fast_NormalizeWordFolder *nwf = new Fast_NormalizeWordFolder();
        delete nwf;

        return true;
    }

    bool TokenizeAnnotatedBuffer() {
        Fast_NormalizeWordFolder *nwf = new Fast_NormalizeWordFolder();
        const char *testinput = "This is a "
                                "\xEF\xBF\xB9" "café" "\xEF\xBF\xBA" "cafe" "\xEF\xBF\xBB"
                                " superduperextrafeaturecoolandlongplainword fun "
                                "\xEF\xBF\xB9" "www" "\xEF\xBF\xBA"
                                "world wide web extra long annotation block" "\xEF\xBF\xBB"
                                " test\nIt is cool.\n";
        const char *correct[] = {
            "this", "is", "a",
            "\xEF\xBF\xB9" "café" "\xEF\xBF\xBA" "cafe" "\xEF\xBF\xBB",
            "superduperextrafeaturecool", "fun",
            "\xEF\xBF\xB9" "www" "\xEF\xBF\xBA" "world wide web ex",
            "test", "it", "is", "cool" };
        const char *teststart = testinput;
        const char *testend = testinput + strlen(testinput);
        char destbuf[32];
        char *destbufend = destbuf + 32;
        const char *origstart = testinput;
        size_t tokenlen = 0;

        int tokencounter = 0;
        bool success = true;
        while (
                (teststart
                 = nwf->Tokenize(teststart, testend,
                                 destbuf, destbufend,
                                 origstart, tokenlen)) < testend) {
            // printf("found: %s, correct: %s\n", destbuf, correct[tokencounter]);
            success &= strcmp(destbuf, correct[tokencounter++]) == 0;
        }

        delete nwf;

        return success;
    }

    bool TokenizeAnnotatedUCS4Buffer() {
        Fast_NormalizeWordFolder *nwf = new Fast_NormalizeWordFolder();
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
        bool success = true;
        while (
                (teststart
                 = nwf->UCS4Tokenize(teststart, testend,
                                     destbuf, destbufend,
                                     origstart, tokenlen)) < testend) {
            success &= Fast_UnicodeUtil::utf8cmp(correct[tokencounter++], destbuf) == 0;
        }

        delete nwf;

        return success;
    }

    bool AccentRemovalTest() {
        auto freefunction = [] (char * ptr) { free(ptr); };
        auto input = std::unique_ptr<char, decltype(freefunction)>(Fast_UnicodeUtil::strdupLAT1("����������������������������������������������������������������������������������������������p�!"),
                                                                   freefunction);
        auto yelloutput = std::unique_ptr<char, decltype(freefunction)>(Fast_UnicodeUtil::strdupLAT1("�������������������������������AAAAAEAAAECEEEEIIIIDNOOOOOE�OEUUUUEYTHssaaaaaeaaaeceeeeiiiidnoooooe�oeuuuueythpth!"),
                                                                        freefunction);
        Fast_NormalizeWordFolder wordfolder;
        int len = wordfolder.FoldedSizeAsUTF8(input.get());
        auto fastliboutput = std::unique_ptr<char[]>(new char[len + 1]);
        wordfolder.FoldUTF8WordToUTF8Quick(fastliboutput.get(), input.get());
        fastliboutput[len] = '\0';
        printf("\n%s\n", yelloutput.get());
        printf("%s\n", fastliboutput.get());
        return strcasecmp(yelloutput.get(), fastliboutput.get()) == 0;
    }


public:

    void Run() override {
        // do the tests
        _test(NormalizeWordFolderConstruction());
        _test(TokenizeAnnotatedBuffer());
        _test(TokenizeAnnotatedUCS4Buffer());
        _test(AccentRemovalTest());
    }
};

class WordFoldersTestApp : public FastOS_Application
{
public:
    int Main() override;
};
