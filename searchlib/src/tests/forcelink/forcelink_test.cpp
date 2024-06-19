// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("forcelink_test");
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/expression/forcelink.hpp>
#include <vespa/searchlib/aggregation/forcelink.hpp>

TEST("forcelink_test") {
    forcelink_searchlib_expression();
    forcelink_searchlib_aggregation();
}

TEST_MAIN() { TEST_RUN_ALL(); }
