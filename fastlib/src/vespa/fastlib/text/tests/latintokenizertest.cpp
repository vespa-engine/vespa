// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "latintokenizertest.h"

int LatinTokenizerTestApp::Main()
{
    LatinTokenizerTest lta;
    lta.SetStream(&std::cout);
    lta.Run();
    return lta.Report();
}

FASTOS_MAIN(LatinTokenizerTestApp);
