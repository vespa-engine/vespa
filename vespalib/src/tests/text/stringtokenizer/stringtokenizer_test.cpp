// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("stringtokenizer_test");
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <set>

using namespace vespalib;

TEST(StringTokenizerTest, stringtokenizer_test) {
    {
        std::string s("This,is ,a,,list ,\tof,,sepa rated\n, \rtokens,");
        StringTokenizer tokenizer(s);
        std::vector<std::string> result;
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

        EXPECT_EQ(result.size(),
                             static_cast<size_t>(tokenizer.size()));
        for (unsigned int i=0; i<result.size(); i++)
            EXPECT_EQ(result[i], tokenizer[i]);
        std::set<std::string> sorted(tokenizer.begin(), tokenizer.end());
        EXPECT_EQ(static_cast<size_t>(8u), sorted.size());

        tokenizer.removeEmptyTokens();
        EXPECT_EQ(7u, tokenizer.size());
    }
    {
        std::string s("\tAnother list with some \ntokens, and stuff.");
        StringTokenizer tokenizer(s, " \t\n", ",.");
        std::vector<std::string> result;
        result.push_back("");
        result.push_back("Another");
        result.push_back("list");
        result.push_back("with");
        result.push_back("some");
        result.push_back("");
        result.push_back("tokens");
        result.push_back("and");
        result.push_back("stuff");

        EXPECT_EQ(result.size(),
                             static_cast<size_t>(tokenizer.size()));
        for (unsigned int i=0; i<result.size(); i++)
            EXPECT_EQ(result[i], tokenizer[i]);
        std::set<std::string> sorted(tokenizer.begin(), tokenizer.end());
        EXPECT_EQ(static_cast<size_t>(8u), sorted.size());

        tokenizer.removeEmptyTokens();
        EXPECT_EQ(7u, tokenizer.size());
    }
    {
        std::string s(" ");
        StringTokenizer tokenizer(s);
        EXPECT_EQ(0u, tokenizer.size());
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
