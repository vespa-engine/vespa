// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST(RequestDumperTest, dump_request) {
    RequestDumper f1;
    f1.handle(Request::UP(new Request()));
}

GTEST_MAIN_RUN_ALL_TESTS()
