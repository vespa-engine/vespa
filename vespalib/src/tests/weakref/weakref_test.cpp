// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/weakref.h>
#include <vespa/vespalib/testkit/testapp.h>

using vespalib::WeakRef;

class Test : public vespalib::TestApp
{
public:
    int getFive() { return 5; }
    void testSimple();
    int Main() override;
};


void
Test::testSimple()
{
    WeakRef<Test>::Owner owner(this);
    WeakRef<Test>        ref(owner);
    {
        WeakRef<Test>::Usage use(ref);
        ASSERT_TRUE(use.valid());
        EXPECT_TRUE(use->getFive() == 5);
    }
    owner.clear();
    {
        WeakRef<Test>::Usage use(ref);
        EXPECT_TRUE(!use.valid());
    }
}


int
Test::Main()
{
    TEST_INIT("weakref_test");
    testSimple();
    TEST_DONE();
}

TEST_APPHOOK(Test)
