// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::docsummary { class KeywordExtractor; }

class ExtractKeywordsTest
{
private:
    ExtractKeywordsTest(const ExtractKeywordsTest &);
    ExtractKeywordsTest& operator=(const ExtractKeywordsTest &);

    search::docsummary::KeywordExtractor *_extractor;

    int Usage(char *progname);
    bool ShowResult(int testNo, const char *actual, const char *correct);
    bool RunTest(int i, bool verify);

public:
    ExtractKeywordsTest()
        : _extractor(nullptr)
    {}
    int main(int argc, char **argv);
};

