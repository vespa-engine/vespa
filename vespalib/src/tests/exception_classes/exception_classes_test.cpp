// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/exceptions.h>

using namespace vespalib;

TEST("require that PortListenException retains relevant information") {
    PortListenException error(80, "HTTP", "details", VESPA_STRLOC, 0);
    try {
        error.throwSelf();
        ASSERT_TRUE(false);
    } catch(PortListenException e) {
        fprintf(stderr, "what: %s\n", e.what());
        EXPECT_EQUAL(80, e.get_port());
        EXPECT_EQUAL("HTTP", e.get_protocol());
        EXPECT_TRUE(e.getCause() == nullptr);
    }
}

TEST("require that PortListenException with cause retains relevant information") {
    Exception root("root");
    PortListenException error(1337, "RPC", root, "details", VESPA_STRLOC, 0);
    try {
        error.throwSelf();
        ASSERT_TRUE(false);
    } catch(PortListenException e) {
        fprintf(stderr, "what: %s\n", e.what());
        EXPECT_EQUAL(1337, e.get_port());
        EXPECT_EQUAL("RPC", e.get_protocol());
        EXPECT_TRUE(e.getCause() != nullptr);
        EXPECT_TRUE(e.getCause() != &root);
        EXPECT_EQUAL("root", e.getCause()->getMessage());
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
