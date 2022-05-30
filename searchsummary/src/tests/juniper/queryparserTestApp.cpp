// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "queryparserTest.h"
#include "testenv.h"
#include <vespa/vespalib/testkit/testapp.h>


int main(int argc, char **argv) {
    juniper::TestEnv te(argc, argv, TEST_PATH("./testclient.rc").c_str());
    QueryParserTest test;
    test.SetStream(&std::cout);
    test.Run(argc, argv);
    return (int)test.Report();
}
