// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST(ServerTaggerTest, server_tagger) {
    RequestReceptor f1;
    ServerTagger f2(ServerSpec("host", 42), f1);
    Request::UP req(new Request());
    EXPECT_EQ("", req->server().host);
    EXPECT_EQ(0, req->server().port);
    f2.handle(std::move(req));
    ASSERT_TRUE(f1.request.get() != 0);
    EXPECT_EQ("host", f1.request->server().host);
    EXPECT_EQ(42, f1.request->server().port);
}

GTEST_MAIN_RUN_ALL_TESTS()
