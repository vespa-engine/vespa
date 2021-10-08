// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "queryparserTest.h"
#include "testenv.h"
#include <vespa/vespalib/testkit/testapp.h>

/**
 * The QueryParserTestApp class is the main routine for running the unit
 * tests for the QueryParser class in isolation.
 *
 * @sa QueryParser @author Knut Omang
 */
class QueryParserTestApp : public vespalib::TestApp {
public:
    int Main() override {
        juniper::TestEnv te(this, TEST_PATH("../rpclient/testclient.rc").c_str());
        QueryParserTest test;
        test.SetStream(&std::cout);
        test.Run(_argc, _argv);
        return (int)test.Report();
    }
};

FASTOS_MAIN(QueryParserTestApp);
