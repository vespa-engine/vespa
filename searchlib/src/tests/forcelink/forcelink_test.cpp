// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("forcelink_test");
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/searchlib/expression/forcelink.hpp>
#include <vespa/searchlib/aggregation/forcelink.hpp>

TEST(ForcelinkTest, forcelink_expression_and_aggregation) {
    forcelink_searchlib_expression();
    forcelink_searchlib_aggregation();
}

GTEST_MAIN_RUN_ALL_TESTS()
