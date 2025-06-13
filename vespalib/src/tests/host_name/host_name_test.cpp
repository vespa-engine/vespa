// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/host_name.h>

using namespace vespalib;

TEST(HostNameTest, require_that_host_name_can_be_obtained) {
    EXPECT_NE("", HostName::get());
}

GTEST_MAIN_RUN_ALL_TESTS()
