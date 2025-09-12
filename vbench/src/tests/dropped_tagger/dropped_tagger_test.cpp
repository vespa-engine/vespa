// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST(DroppedTaggerTest, dropped_tagger) {
    RequestReceptor f1;
    DroppedTagger f2(f1);
    auto req = std::make_unique<Request>();
    EXPECT_EQ(Request::STATUS_OK, req->status());
    f2.handle(std::move(req));
    ASSERT_TRUE(f1.request.get() != nullptr);
    EXPECT_EQ(Request::STATUS_DROPPED, f1.request->status());
}

GTEST_MAIN_RUN_ALL_TESTS()
