// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastlib/testsuite/test.h>
#include <vespa/fastlib/text/unicodeutil.h>
#include <vespa/fastos/app.h>

class UnicodeUtilTest : public Test
{
    bool GetUTF8Char_WrongInput() {
        const char *testdata = "ab\xF8";

        ucs4_t the_char = 0;

        const unsigned char *src = reinterpret_cast<const unsigned char *>(testdata);
        while (*src != 0) {
            the_char = Fast_UnicodeUtil::GetUTF8Char(src);
            // fprintf(stderr, "GetUTF8Char_WrongInput(): the_char = U+%04X\n", the_char);
        }
        return (the_char == Fast_UnicodeUtil::_BadUTF8Char);
    }
    bool IsTerminalPunctuationChar(char ch, bool b) {
        if (Fast_UnicodeUtil::IsTerminalPunctuationChar(ch) != b) {
            printf("expected char '%c' %s terminal punctuation char\n", ch, b ? "to be" : "not to be");
            return false;
        }
        return true;
    }

    bool IsTerminalPunctuationChar() {
        // test a small selection
        bool retval = true;
        retval &= IsTerminalPunctuationChar('!', true);
        retval &= IsTerminalPunctuationChar(',', true);
        retval &= IsTerminalPunctuationChar('.', true);
        retval &= IsTerminalPunctuationChar(':', true);
        retval &= IsTerminalPunctuationChar(';', true);
        retval &= IsTerminalPunctuationChar(' ', false);
        retval &= IsTerminalPunctuationChar('a', false);
        retval &= IsTerminalPunctuationChar('A', false);
        return retval;
    }

public:
    void Run() override {
        // do the tests
        _test(GetUTF8Char_WrongInput());
        _test(IsTerminalPunctuationChar());
    }
};

class UnicodeUtilTestApp : public FastOS_Application
{
public:
    int Main() override;
};
