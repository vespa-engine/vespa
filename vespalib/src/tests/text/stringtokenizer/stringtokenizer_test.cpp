// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("stringtokenizer_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <set>

using namespace vespalib;

TEST_SETUP(Test);

int
Test::Main()
{
    TEST_INIT("stringtokenizer_test");
    {
        string s("This,is ,a,,list ,\tof,,sepa rated\n, \rtokens,");
        StringTokenizer tokenizer(s);
        std::vector<string> result;
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

        EXPECT_EQUAL(result.size(),
                             static_cast<size_t>(tokenizer.size()));
        for (unsigned int i=0; i<result.size(); i++)
            EXPECT_EQUAL(result[i], tokenizer[i]);
        std::set<string> sorted(tokenizer.begin(), tokenizer.end());
        EXPECT_EQUAL(static_cast<size_t>(8u), sorted.size());

        tokenizer.removeEmptyTokens();
        EXPECT_EQUAL(7u, tokenizer.size());
    }
    {
        string s("\tAnother list with some \ntokens, and stuff.");
        StringTokenizer tokenizer(s, " \t\n", ",.");
        std::vector<string> result;
        result.push_back("");
        result.push_back("Another");
        result.push_back("list");
        result.push_back("with");
        result.push_back("some");
        result.push_back("");
        result.push_back("tokens");
        result.push_back("and");
        result.push_back("stuff");

        EXPECT_EQUAL(result.size(),
                             static_cast<size_t>(tokenizer.size()));
        for (unsigned int i=0; i<result.size(); i++)
            EXPECT_EQUAL(result[i], tokenizer[i]);
        std::set<string> sorted(tokenizer.begin(), tokenizer.end());
        EXPECT_EQUAL(static_cast<size_t>(8u), sorted.size());

        tokenizer.removeEmptyTokens();
        EXPECT_EQUAL(7u, tokenizer.size());
    }
    {
        string s(" ");
        StringTokenizer tokenizer(s);
        EXPECT_EQUAL(0u, tokenizer.size());
    }
    TEST_DONE();
}
