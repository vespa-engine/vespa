// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/searchlib/transactionlog/translogserver.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>

using search::index::DummyFileHeaderContext;
using search::transactionlog::TransLogServer;


int main(int argc, char *argv[])
{
    if ((argc > 1) && (argv[0] != NULL)) {
        DummyFileHeaderContext::setCreator(argv[0]);
    }
    DummyFileHeaderContext fileHeaderContext;
    TransLogServer tls("test7", 18378, ".", fileHeaderContext, 0x10000);
    sleep(60);
    return 0;
}
