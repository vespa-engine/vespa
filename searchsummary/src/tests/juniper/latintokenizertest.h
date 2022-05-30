// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "test.h"
#include <vespa/juniper/latintokenizer.h>
#include <vespa/vespalib/util/stringfmt.h>

class Mapel_Pucntuation {
private:
    /** Member variables. */
    static bool *_lookup;
public:

    /** Constructors */
    Mapel_Pucntuation();

    /** Punctuation predicate. */
    bool operator()(char c) const {
        return _lookup[static_cast<unsigned char>(c)];
    }

};

class Maple_Space {
private:

    /** Member variables. */
    static bool *_lookup;

public:

    /** Constructors */
    Maple_Space();

    /** Space predicate. */
    bool operator()(char c) const {
        return _lookup[static_cast<unsigned char>(c)];
    }

};

bool *Maple_Space::_lookup       = NULL;
bool *Mapel_Pucntuation::_lookup = NULL;

Mapel_Pucntuation::Mapel_Pucntuation() {

    // Initialize lookup table.
    if (_lookup == NULL) {

        _lookup = new bool[256];

        for (unsigned int i = 0; i < 256; ++i) {
            _lookup[i] = false;
        }

        _lookup[static_cast<unsigned char>('.')]  = true;
        _lookup[static_cast<unsigned char>(',')]  = true;
        _lookup[static_cast<unsigned char>(':')]  = true;
        _lookup[static_cast<unsigned char>(';')]  = true;
        _lookup[static_cast<unsigned char>('|')]  = true;
        _lookup[static_cast<unsigned char>('!')]  = true;
        _lookup[static_cast<unsigned char>('?')]  = true;
        _lookup[static_cast<unsigned char>('@')]  = true;
        _lookup[static_cast<unsigned char>('/')]  = true;
        _lookup[static_cast<unsigned char>('(')]  = true;
        _lookup[static_cast<unsigned char>(')')]  = true;
        _lookup[static_cast<unsigned char>('[')]  = true;
        _lookup[static_cast<unsigned char>(']')]  = true;
        _lookup[static_cast<unsigned char>('{')]  = true;
        _lookup[static_cast<unsigned char>('}')]  = true;
        _lookup[static_cast<unsigned char>('<')]  = true;
        _lookup[static_cast<unsigned char>('>')]  = true;
        _lookup[static_cast<unsigned char>('*')]  = true;
        _lookup[static_cast<unsigned char>('=')]  = true;
        _lookup[static_cast<unsigned char>('%')]  = true;
        _lookup[static_cast<unsigned char>('\\')] = true;

    }

}

Maple_Space::Maple_Space() {

    // Initialize lookup table.
    if (_lookup == NULL) {

        _lookup = new bool[256];

        for (unsigned int i = 0; i < 256; ++i) {
            _lookup[i] = false;
        }

        _lookup[static_cast<unsigned char>(' ')]  = true;
        _lookup[static_cast<unsigned char>('\n')] = true;
        _lookup[static_cast<unsigned char>('\t')] = true;
        _lookup[static_cast<unsigned char>('\r')] = true;
        _lookup[static_cast<unsigned char>('"')]  = true;
        _lookup[static_cast<unsigned char>('\'')] = true;
        _lookup[static_cast<unsigned char>('`')]  = true;
        _lookup[static_cast<unsigned char>('_')]  = true;

    }
}

class LatinTokenizerTest : public Test
{
private:
    void TestSimple();
    void TestSimpleLength();
    void TestEnding();
    void TestEndingLength();
    void TestNull();
    void TestNullLength();
    void TestEmpty();
    void TestEmptyLength();
    void TestMapelURL();

    template <typename IsSeparator, typename IsPunctuation>
    void TestWord(Fast_LatinTokenizer<IsSeparator,IsPunctuation>* lt,
                  const char* correct,
                  bool punct = false)
    {
        typename Fast_LatinTokenizer<IsSeparator,IsPunctuation>::Fast_Token token;
        _test(lt->MoreTokens());

        token = lt->GetNextToken();
        char temp = *token.second;
        *token.second = '\0';
        vespalib::string word = vespalib::make_string("%s", token.first);
        *token.second = temp;

        PushDesc(vespalib::make_string("%s%s == %s", "word: ", word.c_str(), correct).c_str());

        _test(word == correct);

        _test(token._punctuation == punct);

        PopDesc();
    }

    void TestTypeparamObservers();

public:
    LatinTokenizerTest();
    ~LatinTokenizerTest();
    void Run() override;
};


LatinTokenizerTest::LatinTokenizerTest()
{

}


LatinTokenizerTest::~LatinTokenizerTest()
{

}


void LatinTokenizerTest::TestSimple()
{
    PushDesc("Simple");

    Fast_SimpleLatinTokenizer lt;
    std::string s("This is. my . test String.");
    lt.SetNewText(const_cast<char*>(s.c_str()));

    PushDesc("This");
    TestWord(&lt, "This");
    PopDesc();
    PushDesc("is");
    TestWord(&lt, "is");
    PopDesc();
    PushDesc(".");
    TestWord(&lt, ".", true);
    PopDesc();
    PushDesc("my");
    TestWord(&lt, "my");
    PopDesc();
    PushDesc(".");
    TestWord(&lt, ".", true);
    PopDesc();
    PushDesc("test");
    TestWord(&lt, "test");
    PopDesc();
    PushDesc("String");
    TestWord(&lt, "String");
    PopDesc();
    PushDesc(".");
    TestWord(&lt, ".", true);
    PopDesc();

    _test(!lt.MoreTokens());

    PopDesc();
}



void LatinTokenizerTest::TestSimpleLength()
{
    PushDesc("Simple");

    Fast_SimpleLatinTokenizer lt;
    std::string s("This is. my . test String.");
    lt.SetNewText(const_cast<char*>(s.c_str()),
                  s.length());

    PushDesc("This");
    TestWord(&lt, "This");
    PopDesc();
    PushDesc("is");
    TestWord(&lt, "is");
    PopDesc();
    PushDesc(".");
    TestWord(&lt, ".", true);
    PopDesc();
    PushDesc("my");
    TestWord(&lt, "my");
    PopDesc();
    PushDesc(".");
    TestWord(&lt, ".", true);
    PopDesc();
    PushDesc("test");
    TestWord(&lt, "test");
    PopDesc();
    PushDesc("String");
    TestWord(&lt, "String");
    PopDesc();
    PushDesc(".");
    TestWord(&lt, ".", true);
    PopDesc();

    _test(!lt.MoreTokens());

    PopDesc();
}



void LatinTokenizerTest::TestEnding()
{
    PushDesc("Ending\n");

    std::string text("This is   my test String ");
    Fast_SimpleLatinTokenizer* lt = new Fast_SimpleLatinTokenizer(const_cast<char*>(text.c_str()));

    TestWord(lt, "This");
    TestWord(lt, "is");
    TestWord(lt, "my");
    TestWord(lt, "test");
    TestWord(lt, "String");

    _test(!lt->MoreTokens());

    _test(text == lt->GetOriginalText());

    delete lt;

    PopDesc();
}

void LatinTokenizerTest::TestEndingLength()
{
    PushDesc("Ending\n");

    std::string text("This is   my test String ");
    Fast_SimpleLatinTokenizer* lt = new Fast_SimpleLatinTokenizer(const_cast<char*>(text.c_str()),
                                                                  text.length());

    TestWord(lt, "This");
    TestWord(lt, "is");
    TestWord(lt, "my");
    TestWord(lt, "test");
    TestWord(lt, "String");

    _test(!lt->MoreTokens());

    _test(text == std::string(lt->GetOriginalText()));

    delete lt;

    PopDesc();
}

void LatinTokenizerTest::TestNull()
{
    PushDesc("Null\n");

    Fast_SimpleLatinTokenizer* lt = new Fast_SimpleLatinTokenizer(NULL);

    _test(!lt->MoreTokens());

    _test(lt->GetOriginalText() == NULL);

    delete lt;

    PopDesc();
}

void LatinTokenizerTest::TestNullLength()
{
    PushDesc("Null\n");

    Fast_SimpleLatinTokenizer* lt = new Fast_SimpleLatinTokenizer(NULL, 0);

    _test(!lt->MoreTokens());

    _test(lt->GetOriginalText() == NULL);

    delete lt;

    PopDesc();
}

void LatinTokenizerTest::TestEmpty()
{
    PushDesc("Empty\n");

    std::string text(" ");
    Fast_SimpleLatinTokenizer* lt = new Fast_SimpleLatinTokenizer(const_cast<char*>(text.c_str()));

    _test(!lt->MoreTokens());

    delete lt;

    PopDesc();
}

void LatinTokenizerTest::TestEmptyLength()
{
    PushDesc("Empty\n");

    std::string text(" ");
    Fast_SimpleLatinTokenizer* lt = new Fast_SimpleLatinTokenizer(const_cast<char*>(text.c_str()),
                                                                  text.length());

    _test(!lt->MoreTokens());

    delete lt;

    PopDesc();
}


class TPS
{
private:
    TPS(const TPS &);
    TPS& operator=(const TPS &);

public:
    TPS() : _myfunc(NULL) {}
    void Init(int (*myfunc)(int c))
    {
        _myfunc = myfunc;
    }

    bool operator()(char c)
    {
//      LatinTokenizerTest::_test(_myfunc);
        return (_myfunc(c) != 0);
    }

private:
    int (*_myfunc)(int c);
};

void LatinTokenizerTest::TestTypeparamObservers()
{

    typedef Fast_LatinTokenizer<TPS,TPS> MyTokenizer;

    PushDesc("TypeparamObservers\n");
    std::string text("4Some6text");
    MyTokenizer* tok = new MyTokenizer(const_cast<char*>(text.c_str()));
    tok->GetIsPunctuation().Init(ispunct);
    tok->GetIsSeparator().Init(isdigit);

    TestWord(tok,"Some");
    TestWord(tok,"text");
    _test(!tok->MoreTokens());
    PopDesc();

    delete tok;
}

void LatinTokenizerTest::TestMapelURL()
{

    typedef Fast_LatinTokenizer<Maple_Space, Mapel_Pucntuation> MyTokenizer;

    PushDesc("MapelURL\n");
    std::string text("http://search.msn.co.uk/results.asp?q= cfg=SMCBROWSE rn=1825822 dp=1873075 v=166:");
    MyTokenizer* tok = new MyTokenizer(const_cast<char*>(text.c_str()));

    TestWord(tok,"http", false);
    TestWord(tok,":", true);
    TestWord(tok,"/", true);
    TestWord(tok,"/", true);
    TestWord(tok,"search", false);
    TestWord(tok,".", true);
    TestWord(tok,"msn", false);
    TestWord(tok,".", true);
    TestWord(tok,"co", false);
    TestWord(tok,".", true);
    TestWord(tok,"uk", false);
    TestWord(tok,"/", true);
    TestWord(tok,"results", false);
    TestWord(tok,".", true);
    TestWord(tok,"asp", false);
    TestWord(tok,"?", true);
    TestWord(tok,"q", false);
    TestWord(tok,"=", true);
    TestWord(tok,"cfg", false);
    TestWord(tok,"=", true);
    TestWord(tok,"SMCBROWSE", false);
    TestWord(tok,"rn", false);
    TestWord(tok,"=", true);
    TestWord(tok,"1825822", false);
    TestWord(tok,"dp", false);
    TestWord(tok,"=", true);
    TestWord(tok,"1873075", false);
    TestWord(tok,"v", false);
    TestWord(tok,"=", true);
    TestWord(tok,"166", false);
    TestWord(tok,":", true);
    _test(!tok->MoreTokens());
    PopDesc();

    delete tok;
}



void LatinTokenizerTest::Run()
{
    TestSimple();
    TestSimpleLength();
    TestEnding();
    TestEndingLength();
    TestNull();
    TestNullLength();
    TestEmpty();
    TestEmptyLength();
    TestTypeparamObservers();
    TestMapelURL();
}
