// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/messagebus/network/identity.h>

using namespace mbus;

TEST_SETUP(Test);

int
Test::Main()
{
    TEST_INIT("identity_test");
    Identity ident("foo/bar/baz");
    EXPECT_TRUE(ident.getServicePrefix() == "foo/bar/baz");
    {
        std::vector<string> tmp = Identity::split("foo/bar/baz");
        ASSERT_TRUE(tmp.size() == 3);
        EXPECT_TRUE(tmp[0] == "foo");
        EXPECT_TRUE(tmp[1] == "bar");
        EXPECT_TRUE(tmp[2] == "baz");
    }
    {
        std::vector<string> tmp = Identity::split("//");
        ASSERT_TRUE(tmp.size() == 3);
        EXPECT_TRUE(tmp[0] == "");
        EXPECT_TRUE(tmp[1] == "");
        EXPECT_TRUE(tmp[2] == "");
    }
    {
        std::vector<string> tmp = Identity::split("foo");
        ASSERT_TRUE(tmp.size() == 1);
        EXPECT_TRUE(tmp[0] == "foo");
    }
    {
        std::vector<string> tmp = Identity::split("");
        ASSERT_TRUE(tmp.size() == 1);
        EXPECT_TRUE(tmp[0] == "");
    }
    TEST_DONE();
}
