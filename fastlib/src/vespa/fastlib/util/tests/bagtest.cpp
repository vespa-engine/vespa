// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "bagtest.h"



int BagTest::Main() {
    BagTester bt;
    bt.SetStream(&std::cout);
    bt.Run();
    if (bt.Report() > 0) {
        return 1;
    }

    return 0;
}


FASTOS_MAIN(BagTest)
