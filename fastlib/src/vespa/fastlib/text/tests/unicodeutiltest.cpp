// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "unicodeutiltest.h"

int UnicodeUtilTestApp::Main()
{
    UnicodeUtilTest t;
    t.SetStream(&std::cout);
    t.Run();
    return t.Report();
}

FASTOS_MAIN(UnicodeUtilTestApp)
