// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST(RequestSinkTest, put_a_request_into_the_sink__where_does_it_go__nobody_cares) {
    RequestSink f1;
    f1.handle(Request::UP(new Request()));
}

GTEST_MAIN_RUN_ALL_TESTS()
