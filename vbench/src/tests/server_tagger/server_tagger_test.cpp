// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST_FF("server tagger", RequestReceptor(), ServerTagger(ServerSpec("host", 42), f1)) {
    Request::UP req(new Request());
    EXPECT_EQUAL("", req->server().host);
    EXPECT_EQUAL(0, req->server().port);
    f2.handle(std::move(req));
    ASSERT_TRUE(f1.request.get() != 0);
    EXPECT_EQUAL("host", f1.request->server().host);
    EXPECT_EQUAL(42, f1.request->server().port);
}

TEST_MAIN() { TEST_RUN_ALL(); }
