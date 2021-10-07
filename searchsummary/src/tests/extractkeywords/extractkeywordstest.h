// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fastos/app.h>

namespace search::docsummary { class KeywordExtractor; }

class ExtractKeywordsTest : public FastOS_Application
{
private:
    ExtractKeywordsTest(const ExtractKeywordsTest &);
    ExtractKeywordsTest& operator=(const ExtractKeywordsTest &);

    search::docsummary::KeywordExtractor *_extractor;

    int Main() override;
    int Usage(char *progname);
    bool ShowResult(int testNo, const char *actual, const char *correct);
    bool RunTest(int i, bool verify);

public:
    ExtractKeywordsTest()
        : _extractor(nullptr)
    {}
};

