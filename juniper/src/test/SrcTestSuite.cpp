// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Implementation of the test suite application SrcTestSuite.
 *
 * @file SrcTestSuite.cpp
 *
 * @author Knut Omang
 *
 * @date Created 21 Feb 2003
 *
 * $Id$
 *
 * <pre>
 *              Copyright (c) : 2003 Fast Search & Transfer ASA
 *                              ALL RIGHTS RESERVED
 * </pre>
 ****************************************************************************/
#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/fastlib/testsuite/suite.h>
#include "testenv.h"
#include "mcandTest.h"
#include "queryparserTest.h"
#include "matchobjectTest.h"
#include "auxTest.h"

/**
 * The SrcTestSuite class runs all the unit tests for the src module.
 *
 * @author Knut Omang
 */
class SrcTestSuite : public Suite {

public:
    SrcTestSuite();
};

SrcTestSuite::SrcTestSuite() :
    Suite("SrcTestSuite", &std::cout)
{
    // All tests for this module
    AddTest(new MatchCandidateTest());
    AddTest(new MatchObjectTest());
    AddTest(new QueryParserTest());
    AddTest(new AuxTest());
}

/**
 * The SrcTestSuiteApp class holds the main body for running the
 * SrcTestSuite class.
 *
 * @author Knut Omang
 */
class SrcTestSuiteApp : public vespalib::TestApp {
public:
    virtual int Main() override;
};

int SrcTestSuiteApp::Main() {
    juniper::TestEnv te(this, TEST_PATH("../rpclient/testclient.rc").c_str());
    SrcTestSuite suite;
    suite.Run();
    long failures = suite.Report();
    suite.Free();
    return (int)failures;
}

FASTOS_MAIN(SrcTestSuiteApp);
