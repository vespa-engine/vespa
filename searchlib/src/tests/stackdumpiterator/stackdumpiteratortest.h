// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2001-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <vespa/fastos/app.h>

namespace search { class SimpleQueryStack; }

class StackDumpIteratorTest : public FastOS_Application
{
    int Main() override;
    void Usage(char *progname);
    bool ShowResult(int testNo, search::SimpleQueryStackDumpIterator &actual, search::SimpleQueryStack &correct, unsigned int expected);
    bool RunTest(int i, bool verify);
};
