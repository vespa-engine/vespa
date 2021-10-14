// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mcandTest.h"
#include <vespa/vespalib/testkit/testapp.h>

/**
 * The MatchCandidateTestApp class is the main routine for running the unit
 * tests for the MatchCandidate class in isolation.
 *
 * @sa MatchCandidate @author Knut Omang
 */
class MatchCandidateTestApp : public vespalib::TestApp {
public:
    int Main() override {
        juniper::TestEnv te(this, TEST_PATH("../rpclient/testclient.rc").c_str());
        MatchCandidateTest test;
        test.SetStream(&std::cout);
        test.Run(_argc, _argv);
        return (int)test.Report();
    }
};

FASTOS_MAIN(MatchCandidateTestApp);
