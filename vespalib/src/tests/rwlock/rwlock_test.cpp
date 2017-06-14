// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/rwlock.h>

using namespace vespalib;

class RWLockTest : public TestApp
{
public:
    int Main() override;
    static RWLockReader rbvReader(RWLock & lock) { RWLockReader r(lock); return r; }
    static RWLockWriter rbvWriter(RWLock & lock) { RWLockWriter r(lock); return r; }
};


int
RWLockTest::Main()
{
    TEST_INIT("rwlock_test");

    RWLock lock;
    EXPECT_TRUE(lock._givenLocks == 0);
    {
        EXPECT_TRUE(lock._givenLocks == 0);
        RWLockReader r1(lock);
        EXPECT_TRUE(lock._givenLocks == 1);
        RWLockReader r2(lock);
        EXPECT_TRUE(lock._givenLocks == 2);
        RWLockReader r3(lock);
        EXPECT_TRUE(lock._givenLocks == 3);
    }
    EXPECT_TRUE(lock._givenLocks == 0);
    {
        EXPECT_TRUE(lock._givenLocks == 0);
        RWLockWriter w(lock);
        EXPECT_TRUE(lock._givenLocks == -1);
    }
    EXPECT_TRUE(lock._givenLocks == 0);
    {
        RWLockReader rbv(rbvReader(lock));
        EXPECT_TRUE(lock._givenLocks == 1);
        RWLockReader copy(rbv);
        EXPECT_TRUE(lock._givenLocks == 1);
        RWLockReader copy2(copy);
        EXPECT_TRUE(lock._givenLocks == 1);
    }
    EXPECT_TRUE(lock._givenLocks == 0);
    {
        RWLock lock2;
        RWLockReader copy(rbvReader(lock));
        EXPECT_TRUE(lock._givenLocks == 1);
        RWLockReader copy2(rbvReader(lock2));
        EXPECT_TRUE(lock._givenLocks == 1);
        EXPECT_TRUE(lock2._givenLocks == 1);
        RWLockReader rbv(rbvReader(lock));
        EXPECT_TRUE(lock._givenLocks == 2);
        copy=rbv;
        EXPECT_TRUE(lock._givenLocks == 1);
        copy2=copy;
        EXPECT_TRUE(lock2._givenLocks == 0);
        EXPECT_TRUE(lock._givenLocks == 1);
    }
    EXPECT_TRUE(lock._givenLocks == 0);
    {
        RWLockWriter rbv(rbvWriter(lock));
        EXPECT_TRUE(lock._givenLocks == -1);
        RWLockWriter copy(rbv);
        EXPECT_TRUE(lock._givenLocks == -1);
        RWLockWriter copy2(copy);
        EXPECT_TRUE(lock._givenLocks == -1);
    }
    EXPECT_TRUE(lock._givenLocks == 0);

    TEST_DONE();
}

TEST_APPHOOK(RWLockTest)
