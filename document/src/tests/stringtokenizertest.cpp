// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <cppunit/extensions/HelperMacros.h>
#include <iostream>
#include <set>
#include <sstream>
#include <vespa/vespalib/text/stringtokenizer.h>

using vespalib::StringTokenizer;
using std::string;

class StringTokenizerTest : public CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE(StringTokenizerTest);
    CPPUNIT_TEST(testSimpleUsage);
    CPPUNIT_TEST_SUITE_END();

protected:
    void testSimpleUsage();
};

CPPUNIT_TEST_SUITE_REGISTRATION(StringTokenizerTest);

void StringTokenizerTest::testSimpleUsage()
{
    {
        string s("This,is ,a,,list ,\tof,,sepa rated\n, \rtokens,");
        StringTokenizer tokenizer(s);
        StringTokenizer::TokenList result;
        result.push_back("This");
        result.push_back("is");
        result.push_back("a");
        result.push_back("");
        result.push_back("list");
        result.push_back("of");
        result.push_back("");
        result.push_back("sepa rated");
        result.push_back("tokens");
        result.push_back("");

        CPPUNIT_ASSERT_EQUAL(result.size(),
                             static_cast<size_t>(tokenizer.size()));
        for (unsigned int i=0; i<result.size(); i++) {
            CPPUNIT_ASSERT_EQUAL(result[i], tokenizer[i]);
        }
        std::set<string> sorted(tokenizer.begin(), tokenizer.end());
        CPPUNIT_ASSERT_EQUAL(static_cast<size_t>(8u), sorted.size());

        tokenizer.removeEmptyTokens();
        CPPUNIT_ASSERT_EQUAL(7u, tokenizer.size());
    }
    {
        string s("\tAnother list with some \ntokens, and stuff.");
        StringTokenizer tokenizer(s, " \t\n", ",.");
        StringTokenizer::TokenList result;
        result.push_back("");
        result.push_back("Another");
        result.push_back("list");
        result.push_back("with");
        result.push_back("some");
        result.push_back("");
        result.push_back("tokens");
        result.push_back("and");
        result.push_back("stuff");

        CPPUNIT_ASSERT_EQUAL(result.size(),
                             static_cast<size_t>(tokenizer.size()));
        for (unsigned int i=0; i<result.size(); i++) {
            CPPUNIT_ASSERT_EQUAL(result[i], tokenizer[i]);
        }
        std::set<string> sorted(tokenizer.begin(), tokenizer.end());
        CPPUNIT_ASSERT_EQUAL(static_cast<size_t>(8u), sorted.size());

        tokenizer.removeEmptyTokens();
        CPPUNIT_ASSERT_EQUAL(7u, tokenizer.size());
    }
    {
        string s(" ");
        StringTokenizer tokenizer(s);
        CPPUNIT_ASSERT_EQUAL(0u, tokenizer.size());
    }

    {
        string s("");
        StringTokenizer tokenizer(s);
        CPPUNIT_ASSERT_EQUAL(0u, tokenizer.size());
    }
    {
        // Test that there aren't any problems with using signed chars.
        string s("Here\x01\xff be\xff\xfe dragons\xff");
        StringTokenizer tokenizer(s, "\xff", "\x01 \xfe");
        StringTokenizer::TokenList result;
        result.push_back("Here");
        result.push_back("be");
        result.push_back("dragons");
        result.push_back("");

        CPPUNIT_ASSERT_EQUAL(result.size(),
                             static_cast<size_t>(tokenizer.size()));
        for (unsigned int i=0; i<result.size(); i++) {
            CPPUNIT_ASSERT_EQUAL(result[i], tokenizer[i]);
        }
        std::set<string> sorted(tokenizer.begin(), tokenizer.end());
        CPPUNIT_ASSERT_EQUAL(static_cast<size_t>(4u), sorted.size());

        tokenizer.removeEmptyTokens();
        CPPUNIT_ASSERT_EQUAL(3u, tokenizer.size());
    }
}
