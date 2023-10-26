// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST_F("dump request", RequestDumper()) {
    f1.handle(Request::UP(new Request()));
}

TEST_MAIN() { TEST_RUN_ALL(); }
