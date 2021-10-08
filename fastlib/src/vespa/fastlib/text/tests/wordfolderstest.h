// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
		// Note last encoded characters encoded as octets to avoid interpreting following letters after xNN as part of the encoding of the character
		// See http://en.cppreference.com/w/cpp/language/escape
        auto freefunction = [] (char * ptr) { free(ptr); };
        auto input = std::unique_ptr<char, decltype(freefunction)>(Fast_UnicodeUtil::strdupLAT1("\xA1\xA2\xA3\xA4\xA5\xA6\xA7\xA8\xA9\xAA\xAB\xAC\xAD\xAE\xAF\xB0\xB1\xB2\xB3\xB4\xB5\xB6\xB7\xB8\xB9\xBA\xBB\xBC\xBD\xBE\xBF\xC0\xC1\xC2\xC3\xC4\xC5\xC6\xC7\xC8\xC9\xCA\xCB\xCC\xCD\xCE\xCF\xD0\xD1\xD2\xD3\xD4\xD5\xD6\xD7\xD8\xD9\xDA\xDB\xDC\xDD\xDE\xDF\xE0\xE1\xE2\xE3\xE4\xE5\xE6\xE7\xE8\xE9\xEA\xEB\xEC\xED\xEE\xEF\xF0\xF1\xF2\xF3\xF4\xF5\xF6\xF7\xF8\xF9\xFA\xFB\xFC\xFD\xFE\x70\xFE\x21"),
                                                                   freefunction);
        auto yelloutput = std::unique_ptr<char, decltype(freefunction)>(Fast_UnicodeUtil::strdupLAT1("\xA1\xA2\xA3\xA4\xA5\xA6\xA7\xA8\xA9\xAA\xAB\xAC\xAD\xAE\xAF\xB0\xB1\xB2\xB3\xB4\xB5\xB6\xB7\xB8\xB9\xBA\xBB\xBC\xBD\xBE\277AAAAAEAAAECEEEEIIIIDNOOOOOE\327OEUUUUEYTHssaaaaaeaaaeceeeeiiiidnoooooe\367oeuuuueythpth!"),
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
