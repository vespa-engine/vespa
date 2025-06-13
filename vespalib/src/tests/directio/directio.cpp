// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/fastos/file.h>
#include <string>

using namespace vespalib;

TEST(DirectIOTest, that_DirectIOException_propagates_the_correct_information) {
    const char *msg("The buffer");
    DirectIOException e("file.a", msg, 10, 3);
    EXPECT_EQ(10u, e.getLength());
    EXPECT_EQ(3u, e.getOffset());
    EXPECT_EQ(msg, e.getBuffer());
    EXPECT_EQ(0u, std::string(e.what()).find("DirectIO failed for file 'file.a' buffer="));
    EXPECT_EQ("file.a", e.getFileName());
}

TEST(DirectIOTest, that_DirectIOException_is_thrown_on_unaligned_buf) {
    FastOS_File f("vespalib_directio_test_app");
    f.EnableDirectIO();
    EXPECT_TRUE(f.OpenReadOnly());
    DataBuffer buf(10000, 4_Ki);
    bool caught(false);
    try {
        f.ReadBuf(buf.getFree()+1, 4_Ki, 0);
    } catch (const DirectIOException & e) {
        EXPECT_EQ(4_Ki, e.getLength());
        EXPECT_EQ(0u, e.getOffset());
        EXPECT_EQ(buf.getFree()+1, e.getBuffer());
        EXPECT_EQ(f.GetFileName(), e.getFileName());
        caught = true;
    }
    EXPECT_TRUE(caught);
}

TEST(DirectIOTest, that_DirectIOException_is_thrown_on_unaligned_offset) {
    FastOS_File f("vespalib_directio_test_app");
    f.EnableDirectIO();
    EXPECT_TRUE(f.OpenReadOnly());
    DataBuffer buf(10000, 4_Ki);
    bool caught(false);
    try {
        f.ReadBuf(buf.getFree(), 4_Ki, 1);
    } catch (const DirectIOException & e) {
        EXPECT_EQ(4_Ki, e.getLength());
        EXPECT_EQ(1u, e.getOffset());
        EXPECT_EQ(buf.getFree(), e.getBuffer());
        EXPECT_EQ(f.GetFileName(), e.getFileName());
        caught = true;
    }
    EXPECT_TRUE(caught);
}

GTEST_MAIN_RUN_ALL_TESTS()
