// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "auxTest.h"
#include <vespa/vespalib/testkit/testapp.h>

class AuxTestApp : public vespalib::TestApp
{
public:
    int Main() override;
};



void Usage(char* s)
{
    fprintf(stderr, "Usage: %s [-d debug_level]\n", s);
}


int AuxTestApp::Main()
{
    juniper::TestEnv te(this, TEST_PATH("../rpclient/testclient.rc").c_str());
    AuxTest pta;
    pta.SetStream(&std::cout);
    pta.Run(_argc, _argv);
    return pta.Report();
}

FASTOS_MAIN(AuxTestApp);
