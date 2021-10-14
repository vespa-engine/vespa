// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matchobjectTest.h"
#include "testenv.h"
#include <vespa/vespalib/testkit/testapp.h>

/**
 * The MatchObjectTestApp class is the main routine for running the unit
 * tests for the MatchObject class in isolation.
 *
 * @sa MatchObject @author Knut Omang
 */
class MatchObjectTestApp : public vespalib::TestApp {
public:
    int Main() override {
        juniper::TestEnv te(this, TEST_PATH("../rpclient/testclient.rc").c_str());
        MatchObjectTest test;
        test.SetStream(&std::cout);
        test.Run(_argc, _argv);
        return (int)test.Report();
    }
};

FASTOS_MAIN(MatchObjectTestApp);
