// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace vespalib;

TEST(ExceptionClassesTest, require_that_PortListenException_retains_relevant_information) {
    PortListenException error(80, "HTTP", "details", VESPA_STRLOC, 0);
    try {
        error.throwSelf();
        ASSERT_TRUE(false);
    } catch(PortListenException & e) {
        fprintf(stderr, "what: %s\n", e.what());
        EXPECT_EQ(80, e.get_port());
        EXPECT_EQ("HTTP", e.get_protocol());
        EXPECT_TRUE(e.getCause() == nullptr);
    }
}

TEST(ExceptionClassesTest, require_that_PortListenException_with_cause_retains_relevant_information) {
    Exception root("root");
    PortListenException error(1337, "RPC", root, "details", VESPA_STRLOC, 0);
    try {
        error.throwSelf();
        ASSERT_TRUE(false);
    } catch(PortListenException & e) {
        fprintf(stderr, "what: %s\n", e.what());
        EXPECT_EQ(1337, e.get_port());
        EXPECT_EQ("RPC", e.get_protocol());
        EXPECT_TRUE(e.getCause() != nullptr);
        EXPECT_TRUE(e.getCause() != &root);
        EXPECT_EQ("root", e.getCause()->getMessage());
    }
}

TEST(ExceptionClassesTest, test_that_OOMException_carries_message_forward) {
    const char * M = "This is the simple message.";
    bool caught(false);
    try {
        throw OOMException(M);
        ASSERT_TRUE(false);
    } catch (OOMException & e) {
        EXPECT_EQ(0, strcmp(M, e.what()));
        caught = true;
    }
    EXPECT_TRUE(caught);
}

TEST(ExceptionClassesTest, require_that_rethrow_if_unsafe_will_rethrow_unsafe_exception) {
    try {
        try {
            throw OOMException("my message");
        } catch (const std::exception &e) {
            rethrow_if_unsafe(e);
            FAIL() << "should not be reached";
        }
    } catch (const OOMException &) {}
}

TEST(ExceptionClassesTest, require_that_rethrow_if_unsafe_will_not_rethrow_safe_exception) {
    try {
        throw IllegalArgumentException("my message");
    } catch (const std::exception &e) {
        rethrow_if_unsafe(e);
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
