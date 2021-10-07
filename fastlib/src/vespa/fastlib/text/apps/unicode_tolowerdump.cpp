// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastlib/text/unicodeutil.h>
#include <vespa/fastos/app.h>

class UnicodeToLowerDumpApp : public FastOS_Application
{
public:
    virtual int Main();
};

int UnicodeToLowerDumpApp::Main()
{
    for (ucs4_t testchar = 0; testchar < 0x10000; testchar++) {
        printf("%08x %08x\n", testchar, Fast_UnicodeUtil::ToLower(testchar));
    }
    return 0;
}

FASTOS_MAIN(UnicodeToLowerDumpApp)
