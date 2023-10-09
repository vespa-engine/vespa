// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST_FF("dropped tagger", RequestReceptor(), DroppedTagger(f1)) {
    Request::UP req(new Request());
    EXPECT_EQUAL(Request::STATUS_OK, req->status());
    f2.handle(std::move(req));
    ASSERT_TRUE(f1.request.get() != 0);
    EXPECT_EQUAL(Request::STATUS_DROPPED, f1.request->status());
}

TEST_MAIN() { TEST_RUN_ALL(); }
