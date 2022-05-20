// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "auxTest.h"
#include <vespa/vespalib/testkit/testapp.h>

void Usage(char* s)
{
    fprintf(stderr, "Usage: %s [-d debug_level]\n", s);
}


int main(int argc, char **argv) {
    juniper::TestEnv te(argc, argv, TEST_PATH("./testclient.rc").c_str());
    AuxTest pta;
    pta.SetStream(&std::cout);
    pta.Run(argc, argv);
    return pta.Report();
}
