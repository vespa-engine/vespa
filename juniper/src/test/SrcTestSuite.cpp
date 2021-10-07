// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "testenv.h"
#include "mcandTest.h"
#include "queryparserTest.h"
#include "matchobjectTest.h"
#include "auxTest.h"
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/fastlib/testsuite/suite.h>
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
    int Main() override;
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
