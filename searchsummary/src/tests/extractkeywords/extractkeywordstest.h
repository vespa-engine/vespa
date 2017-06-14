// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2001-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

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
    void Usage(char *progname);
    bool ShowResult(int testNo, const char *actual, const char *correct);
    bool RunTest(int i, bool verify);

public:
    ExtractKeywordsTest()
        : _extractor(nullptr)
    {}
};

