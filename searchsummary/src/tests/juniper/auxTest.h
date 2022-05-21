// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

// Auxiliary tests for juniper - based on Juniper 1.x proximitytest.cpp

#include "testenv.h"
#include "test.h"
#include <map>

class AuxTest : public Test
{
private:
    AuxTest(const AuxTest&);
    AuxTest& operator=(const AuxTest&);
public:
    AuxTest();
    virtual ~AuxTest();

    typedef void(AuxTest::* tst_method_ptr) ();
    typedef std::map<std::string, tst_method_ptr> MethodContainer;
    MethodContainer test_methods_;
    void init();

    void Run(MethodContainer::iterator &itr);
    void Run(const char* method);
    void Run(int argc, char* argv[]);
    void Run() override;
protected:
    /**
     * Since we are running within Emacs, the default behavior of
     * print_progress which includes backspace does not work.
     * We'll use a single '.' instead.
     */
    void print_progress() override { *m_osptr << '.' << std::flush; }
private:
    // tests:
    void TestPropertyMap();
    void TestRerase();
    void TestExample();
    void TestUTF8context();
    void TestJapanese();
    void TestStartHits();
    void TestEndHit();
    void TestJuniperStack();
    void TestUTF811();
    void TestUTF812();
    void TestDoubleWidth();
    void TestPartialUTF8();
    void TestLargeBlockChinese();
    void TestSpecialTokenRegistry();
    void TestWhiteSpacePreserved();

    bool assertChar(ucs4_t act, char exp);

    // Utilities
    char* IsoToUtf8 (const char* iso, size_t size);
    char* Utf8ToIso (const char* iso, size_t size);
    void test_summary(Matcher& m, const char* input, size_t input_len,
                      int size, int matches, int surround, size_t& charsize);
    void TestUTF8(unsigned int size);

    bool _split_char;
    SummaryConfig* _sumconf;
};

