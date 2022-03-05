// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/host_name.h>

using namespace vespalib;

TEST("require that host name can be obtained") {
    EXPECT_NOT_EQUAL("", HostName::get());
}

TEST_MAIN() { TEST_RUN_ALL(); }
