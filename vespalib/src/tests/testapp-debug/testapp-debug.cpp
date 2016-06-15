// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/testkit/test_kit.h>

using namespace vespalib;

TEST_MAIN() {
    system("./vespalib_debug_test_app");
    system("diff lhs.out rhs.out > diff.out");
    EXPECT_EQUAL(system("diff diff.out diff.ref"), 0);
}
