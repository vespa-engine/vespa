// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "latintokenizer.h"
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stringfmt.h>

class Mapel_Pucntuation {
private:
    /** Member variables. */
    static bool* _lookup;

public:
    /** Constructors */
    Mapel_Pucntuation();

    /** Punctuation predicate. */
    bool operator()(char c) const { return _lookup[static_cast<unsigned char>(c)]; }
};

class Maple_Space {
private:
    /** Member variables. */
    static bool* _lookup;

public:
    /** Constructors */
    Maple_Space();

    /** Space predicate. */
    bool operator()(char c) const { return _lookup[static_cast<unsigned char>(c)]; }
};

bool* Maple_Space::_lookup = NULL;
bool* Mapel_Pucntuation::_lookup = NULL;

Mapel_Pucntuation::Mapel_Pucntuation() {

    // Initialize lookup table.
    if (_lookup == NULL) {

        _lookup = new bool[256];

        for (unsigned int i = 0; i < 256; ++i) { _lookup[i] = false; }

        _lookup[static_cast<unsigned char>('.')] = true;
        _lookup[static_cast<unsigned char>(',')] = true;
        _lookup[static_cast<unsigned char>(':')] = true;
        _lookup[static_cast<unsigned char>(';')] = true;
        _lookup[static_cast<unsigned char>('|')] = true;
        _lookup[static_cast<unsigned char>('!')] = true;
        _lookup[static_cast<unsigned char>('?')] = true;
        _lookup[static_cast<unsigned char>('@')] = true;
        _lookup[static_cast<unsigned char>('/')] = true;
        _lookup[static_cast<unsigned char>('(')] = true;
        _lookup[static_cast<unsigned char>(')')] = true;
        _lookup[static_cast<unsigned char>('[')] = true;
        _lookup[static_cast<unsigned char>(']')] = true;
        _lookup[static_cast<unsigned char>('{')] = true;
        _lookup[static_cast<unsigned char>('}')] = true;
        _lookup[static_cast<unsigned char>('<')] = true;
        _lookup[static_cast<unsigned char>('>')] = true;
        _lookup[static_cast<unsigned char>('*')] = true;
        _lookup[static_cast<unsigned char>('=')] = true;
        _lookup[static_cast<unsigned char>('%')] = true;
        _lookup[static_cast<unsigned char>('\\')] = true;
    }
}

Maple_Space::Maple_Space() {

    // Initialize lookup table.
    if (_lookup == NULL) {

        _lookup = new bool[256];

        for (unsigned int i = 0; i < 256; ++i) { _lookup[i] = false; }

        _lookup[static_cast<unsigned char>(' ')] = true;
        _lookup[static_cast<unsigned char>('\n')] = true;
        _lookup[static_cast<unsigned char>('\t')] = true;
        _lookup[static_cast<unsigned char>('\r')] = true;
        _lookup[static_cast<unsigned char>('"')] = true;
        _lookup[static_cast<unsigned char>('\'')] = true;
        _lookup[static_cast<unsigned char>('`')] = true;
        _lookup[static_cast<unsigned char>('_')] = true;
    }
}

template <typename IsSeparator, typename IsPunctuation>
void TestWord(Fast_LatinTokenizer<IsSeparator, IsPunctuation>* lt, const char* correct, bool punct = false) {
    typename Fast_LatinTokenizer<IsSeparator, IsPunctuation>::Fast_Token token;
    EXPECT_TRUE(lt->MoreTokens());

    token = lt->GetNextToken();
    char temp = *token.second;
    *token.second = '\0';
    std::string word = vespalib::make_string("%s", token.first);
    *token.second = temp;

    {
        SCOPED_TRACE(vespalib::make_string("%s%s == %s", "word: ", word.c_str(), correct).c_str());

        EXPECT_TRUE(word == correct);

        EXPECT_TRUE(token._punctuation == punct);

    }
}

TEST(LatinTokenizerTest, testSimple) {

    Fast_SimpleLatinTokenizer lt;
    std::string               s("This is. my . test String.");
    lt.SetNewText(const_cast<char*>(s.c_str()));

    {
        SCOPED_TRACE("This");
        TestWord(&lt, "This");
    }
    {
        SCOPED_TRACE("is");
        TestWord(&lt, "is");
    }
    {
        SCOPED_TRACE(".");
        TestWord(&lt, ".", true);
    }
    {
        SCOPED_TRACE("my");
        TestWord(&lt, "my");
    }
    {
        SCOPED_TRACE(".");
        TestWord(&lt, ".", true);
    }
    {
        SCOPED_TRACE("test");
        TestWord(&lt, "test");
    }
    {
        SCOPED_TRACE("String");
        TestWord(&lt, "String");
    }
    {
        SCOPED_TRACE(".");
        TestWord(&lt, ".", true);
    }

    EXPECT_TRUE(!lt.MoreTokens());
}

TEST(LatinTokenizerTest, testSimpleLength) {

    Fast_SimpleLatinTokenizer lt;
    std::string               s("This is. my . test String.");
    lt.SetNewText(const_cast<char*>(s.c_str()), s.length());

    {
        SCOPED_TRACE("This");
        TestWord(&lt, "This");
    }
    {
        SCOPED_TRACE("is");
        TestWord(&lt, "is");
    }
    {
        SCOPED_TRACE(".");
        TestWord(&lt, ".", true);
    }
    {
        SCOPED_TRACE("my");
        TestWord(&lt, "my");
    }
    {
        SCOPED_TRACE(".");
        TestWord(&lt, ".", true);
    }
    {
        SCOPED_TRACE("test");
        TestWord(&lt, "test");
    }
    {
        SCOPED_TRACE("String");
        TestWord(&lt, "String");
    }
    {
        SCOPED_TRACE(".");
        TestWord(&lt, ".", true);
    }

    EXPECT_TRUE(!lt.MoreTokens());
}

TEST(LatinTokenizerTest, testEnding) {

    std::string                text("This is   my test String ");
    Fast_SimpleLatinTokenizer* lt = new Fast_SimpleLatinTokenizer(const_cast<char*>(text.c_str()));

    TestWord(lt, "This");
    TestWord(lt, "is");
    TestWord(lt, "my");
    TestWord(lt, "test");
    TestWord(lt, "String");

    EXPECT_TRUE(!lt->MoreTokens());

    EXPECT_TRUE(text == lt->GetOriginalText());

    delete lt;
}

TEST(LatinTokenizerTest, testEndingLength) {

    std::string                text("This is   my test String ");
    Fast_SimpleLatinTokenizer* lt = new Fast_SimpleLatinTokenizer(const_cast<char*>(text.c_str()), text.length());

    TestWord(lt, "This");
    TestWord(lt, "is");
    TestWord(lt, "my");
    TestWord(lt, "test");
    TestWord(lt, "String");

    EXPECT_TRUE(!lt->MoreTokens());

    EXPECT_TRUE(text == std::string(lt->GetOriginalText()));

    delete lt;
}

TEST(LatinTokenizerTest, testNull) {

    Fast_SimpleLatinTokenizer* lt = new Fast_SimpleLatinTokenizer(NULL);

    EXPECT_TRUE(!lt->MoreTokens());

    EXPECT_TRUE(lt->GetOriginalText() == NULL);

    delete lt;
}

TEST(LatinTokenizerTest, testNullLength) {

    Fast_SimpleLatinTokenizer* lt = new Fast_SimpleLatinTokenizer(NULL, 0);

    EXPECT_TRUE(!lt->MoreTokens());

    EXPECT_TRUE(lt->GetOriginalText() == NULL);

    delete lt;
}

TEST(LatinTokenizerTest, testEmpty) {

    std::string                text(" ");
    Fast_SimpleLatinTokenizer* lt = new Fast_SimpleLatinTokenizer(const_cast<char*>(text.c_str()));

    EXPECT_TRUE(!lt->MoreTokens());

    delete lt;
}

TEST(LatinTokenizerTest, testEmptyLength) {

    std::string                text(" ");
    Fast_SimpleLatinTokenizer* lt = new Fast_SimpleLatinTokenizer(const_cast<char*>(text.c_str()), text.length());

    EXPECT_TRUE(!lt->MoreTokens());

    delete lt;
}

class TPS {
private:
    TPS(const TPS&);
    TPS& operator=(const TPS&);

public:
    TPS() : _myfunc(NULL) {}
    void Init(int (*myfunc)(int c)) { _myfunc = myfunc; }

    bool operator()(char c) {
        //      LatinTokenizerTest::EXPECT_TRUE(_myfunc);
        return (_myfunc(static_cast<unsigned char>(c)) != 0);
    }

private:
    int (*_myfunc)(int c);
};

TEST(LatinTokenizerTest, testTypeparamObservers) {

    using MyTokenizer = Fast_LatinTokenizer<TPS, TPS>;

    std::string  text("4Some6text");
    MyTokenizer* tok = new MyTokenizer(const_cast<char*>(text.c_str()));
    tok->GetIsPunctuation().Init(std::ispunct);
    tok->GetIsSeparator().Init(std::isdigit);

    TestWord(tok, "Some");
    TestWord(tok, "text");
    EXPECT_TRUE(!tok->MoreTokens());

    delete tok;
}

TEST(LatinTokenizerTest, testMapelURL) {

    using MyTokenizer = Fast_LatinTokenizer<Maple_Space, Mapel_Pucntuation>;

    std::string  text("http://search.msn.co.uk/results.asp?q= cfg=SMCBROWSE rn=1825822 dp=1873075 v=166:");
    MyTokenizer* tok = new MyTokenizer(const_cast<char*>(text.c_str()));

    TestWord(tok, "http", false);
    TestWord(tok, ":", true);
    TestWord(tok, "/", true);
    TestWord(tok, "/", true);
    TestWord(tok, "search", false);
    TestWord(tok, ".", true);
    TestWord(tok, "msn", false);
    TestWord(tok, ".", true);
    TestWord(tok, "co", false);
    TestWord(tok, ".", true);
    TestWord(tok, "uk", false);
    TestWord(tok, "/", true);
    TestWord(tok, "results", false);
    TestWord(tok, ".", true);
    TestWord(tok, "asp", false);
    TestWord(tok, "?", true);
    TestWord(tok, "q", false);
    TestWord(tok, "=", true);
    TestWord(tok, "cfg", false);
    TestWord(tok, "=", true);
    TestWord(tok, "SMCBROWSE", false);
    TestWord(tok, "rn", false);
    TestWord(tok, "=", true);
    TestWord(tok, "1825822", false);
    TestWord(tok, "dp", false);
    TestWord(tok, "=", true);
    TestWord(tok, "1873075", false);
    TestWord(tok, "v", false);
    TestWord(tok, "=", true);
    TestWord(tok, "166", false);
    TestWord(tok, ":", true);
    EXPECT_TRUE(!tok->MoreTokens());

    delete tok;
}

GTEST_MAIN_RUN_ALL_TESTS()
